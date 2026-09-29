param([switch]$KeepBuildDirectory)
# Pure command-line build: javac -> d8 -> aapt2 -> (optional JNI) -> zipalign -> apksigner
# No Gradle / Android Studio / network needed. Bump $VERSION for a new artifact.
#
# NOTE: this file is intentionally ASCII-only.
# Windows PowerShell 5.1 reads .ps1 without a BOM as ANSI(GBK); non-ASCII comment bytes
# can then combine into a stray quote and break parsing ("MissingEndCurlyBrace").
# Keep all Chinese text in the skill docs, not in this script.
$ErrorActionPreference = 'Stop'

$VERSION = '0.3.0'
# Module self-reported versionCode. Declared HERE, next to $VERSION, on purpose:
# the APK file name, module.prop's version= and versionCode= used to be three
# hand-maintained values that could drift apart, which is how a fixed build
# ended up still shipping under the label of the buggy one. build.ps1 now
# injects both into module.prop, so bumping the version is a one-line change.
$VERSION_CODE = 3

$app = $PSScriptRoot
# Toolchain paths. Each can be overridden by an environment variable so the
# script is not tied to one machine; otherwise fall back to the usual locations.
#   ANDROID_SDK        - Android SDK root (needs platforms\android-34 + build-tools)
#   JAVA_HOME_OVERRIDE - JDK 17 root
#   R8_JAR             - optional: a modern r8.jar exposing com.android.tools.r8.D8
$sdk = if ($env:ANDROID_SDK) { $env:ANDROID_SDK }
       elseif (Test-Path 'C:\Android\Sdk') { 'C:\Android\Sdk' }
       else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$jdk = if ($env:JAVA_HOME_OVERRIDE) { $env:JAVA_HOME_OVERRIDE }
       elseif ($env:JAVA_HOME) { $env:JAVA_HOME }
       else { 'C:\Program Files\AdoptOpenJDK\jdk-17.0.0.20-hotspot' }
$tools = Join-Path $sdk 'build-tools\34.0.0'
$androidJarSource = Join-Path $sdk 'platforms\android-34\android.jar'
$javac = Join-Path $jdk 'bin\javac.exe'
$java = Join-Path $jdk 'bin\java.exe'
$jar = Join-Path $jdk 'bin\jar.exe'
$keytool = Join-Path $jdk 'bin\keytool.exe'
$aapt2 = Join-Path $tools 'aapt2.exe'
# Preference order: an explicit R8_JAR > the modern build-tools d8 > older copies.
# The d8 bundled with build-tools\33.0.1 (R8 3.3.20) NPEs on this project, so a
# newer r8 jar is preferred; set R8_JAR if the default selection is not good enough.
$d8Candidates = @()
if ($env:R8_JAR) { $d8Candidates += $env:R8_JAR }
$d8Candidates += (Join-Path $tools 'lib\d8.jar')
$d8Candidates += (Join-Path $sdk 'build-tools\33.0.1\lib\d8.jar')
$d8Jar = $d8Candidates | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -First 1
$zipalign = Join-Path $tools 'zipalign.exe'
$apksigner = Join-Path $tools 'apksigner.bat'
$stage = Join-Path $env:TEMP ('modbuild-' + [guid]::NewGuid().ToString('N'))
$dist = Join-Path $app 'dist'
$keystore = Join-Path $app 'debug.keystore'
$output = Join-Path $dist ("module-v$VERSION.apk")
$env:JAVA_HOME = $jdk
$env:Path = (Join-Path $jdk 'bin') + ';' + $env:Path

foreach ($needed in @($androidJarSource, $javac, $java, $jar, $keytool, $aapt2, $d8Jar, $zipalign, $apksigner)) {
    if (-not (Test-Path -LiteralPath $needed)) { throw "Build tool missing: $needed" }
}
New-Item -ItemType Directory -Path $stage -Force | Out-Null
Copy-Item -LiteralPath $androidJarSource -Destination $stage
$androidJar = Join-Path $stage 'android.jar'
foreach ($folder in @('stub-src', 'src', 'res', 'META-INF', 'libs')) {
    $from = Join-Path $app $folder
    if (Test-Path -LiteralPath $from) { Copy-Item -LiteralPath $from -Destination $stage -Recurse -Force }
}
Copy-Item -LiteralPath (Join-Path $app 'AndroidManifest.xml') -Destination $stage

# Inject the version into module.prop so the APK file name and the version the
# module reports inside the framework can never disagree.
$moduleProp = Join-Path $stage 'META-INF\xposed\module.prop'
if (Test-Path -LiteralPath $moduleProp) {
    $prop = [IO.File]::ReadAllText($moduleProp, [Text.Encoding]::UTF8)
    $prop = [regex]::Replace($prop, '(?m)^version=.*$', "version=$VERSION")
    $prop = [regex]::Replace($prop, '(?m)^versionCode=.*$', "versionCode=$VERSION_CODE")
    [IO.File]::WriteAllText($moduleProp, $prop, (New-Object Text.UTF8Encoding($false)))
    Write-Host "module.prop -> version=$VERSION versionCode=$VERSION_CODE"
}

foreach ($folder in @('stubs', 'classes', 'dex', 'out')) {
    New-Item -ItemType Directory -Path (Join-Path $stage $folder) -Force | Out-Null
}

function Run-Native([string]$name, [scriptblock]$action) {
    & $action
    if ($LASTEXITCODE -ne 0) { throw "$name failed with exit code $LASTEXITCODE" }
}

function Get-FilesByExtension([string]$dir, [string]$ext, [switch]$Recurse) {
    # Deliberately not using Get-ChildItem -Filter: on paths containing CJK characters,
    # PS 5.1 was observed returning 0 matches intermittently for the same directory.
    if (-not (Test-Path -LiteralPath $dir)) { return @() }
    $items = if ($Recurse) { Get-ChildItem -LiteralPath $dir -Recurse -File } else { Get-ChildItem -LiteralPath $dir -File }
    return @($items | Where-Object { $_.Extension -eq $ext } | ForEach-Object FullName)
}

try {
    $stubs = Get-FilesByExtension (Join-Path $stage 'stub-src') '.java' -Recurse
    $sources = Get-FilesByExtension (Join-Path $stage 'src') '.java' -Recurse
    $libJars = Get-FilesByExtension (Join-Path $stage 'libs') '.jar'
    if ($libJars.Count -eq 0) { throw "No jar found under $stage\libs (service-classes.jar is required)" }

    Write-Host 'Compiling API stubs and module'
    Run-Native 'API stub compilation' { & $javac -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath $androidJar -d (Join-Path $stage 'stubs') @stubs }
    $compilePath = (@((Join-Path $stage 'stubs')) + $libJars) -join ';'
    Run-Native 'Module compilation' { & $javac -encoding UTF-8 -nowarn -source 8 -target 8 -bootclasspath $androidJar -classpath $compilePath -d (Join-Path $stage 'classes') @sources }
    $classesJar = Join-Path $stage 'classes.jar'
    Run-Native 'JAR creation' { & $jar -cf $classesJar -C (Join-Path $stage 'classes') . }
    $dexInputs = @($classesJar) + $libJars
    Run-Native 'DEX conversion' { & $java -Xmx3072M -cp $d8Jar com.android.tools.r8.D8 --min-api 26 --lib $androidJar --output (Join-Path $stage 'dex') @dexInputs }

    Write-Host 'Packaging Android resources'
    $compiledRes = Join-Path $stage 'resources.zip'
    Run-Native 'Resource compilation' { & $aapt2 compile --dir (Join-Path $stage 'res') -o $compiledRes }
    $unsigned = Join-Path $stage 'out\module.apk'
    Run-Native 'APK linking' { & $aapt2 link -o $unsigned --manifest (Join-Path $stage 'AndroidManifest.xml') -I $androidJar --min-sdk-version 26 --target-sdk-version 34 $compiledRes }

    # Add dex + META-INF with the JDK jar tool.
    # Not using .NET ZipFile: PS 5.1 sometimes fails to resolve
    # [System.IO.Compression.ZipArchiveMode] and throws TypeNotFound.
    Run-Native 'DEX packaging' { & $jar -uf $unsigned -C (Join-Path $stage 'dex') . }
    Run-Native 'Metadata packaging' { & $jar -uf $unsigned -C $stage META-INF }

    # JNI native libs (e.g. DexKit): must stay uncompressed or System.loadLibrary fails.
    $jniDir = Join-Path $app 'jni'
    if (Test-Path -LiteralPath $jniDir) {
        Copy-Item -LiteralPath $jniDir -Destination (Join-Path $stage 'lib') -Recurse -Force
        Run-Native 'Native library packaging' { & $jar -0uf $unsigned -C $stage lib }
    }

    $aligned = Join-Path $stage 'out\module-aligned.apk'
    Run-Native 'APK alignment' { & $zipalign -f 4 $unsigned $aligned }

    # Local test key: keep app\debug.keystore. Changing the signature forces an uninstall,
    # which wipes the user's settings.
    $env:MOD_TEST_KEYPASS = 'android'
    if (-not (Test-Path -LiteralPath $keystore)) {
        Run-Native 'Test key generation' { & $keytool -genkeypair -keystore $keystore -storepass:env MOD_TEST_KEYPASS -keypass:env MOD_TEST_KEYPASS -alias modtest -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=LocalTest, O=Local, C=CN' }
    }
    New-Item -ItemType Directory -Path $dist -Force | Out-Null
    Run-Native 'APK signing' { & $apksigner sign --ks $keystore --ks-key-alias modtest --ks-pass env:MOD_TEST_KEYPASS --key-pass env:MOD_TEST_KEYPASS --out $output $aligned }
    Run-Native 'Signature verification' { & $apksigner verify $output }
    Write-Host "Built $output"
    (Get-FileHash -LiteralPath $output -Algorithm SHA256).Hash
} finally {
    Remove-Item Env:MOD_TEST_KEYPASS -ErrorAction SilentlyContinue
    if (-not $KeepBuildDirectory -and (Test-Path -LiteralPath $stage)) {
        $resolved = [IO.Path]::GetFullPath($stage)
        $tempRoot = [IO.Path]::GetFullPath($env:TEMP).TrimEnd('\') + '\'
        if (-not $resolved.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase) -or
            -not [IO.Path]::GetFileName($resolved).StartsWith('modbuild-')) {
            throw "Refusing to remove unexpected build directory: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
