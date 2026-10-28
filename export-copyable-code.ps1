$ErrorActionPreference = 'Stop'
$projectRoot = $PSScriptRoot
$outputPath = Join-Path $projectRoot 'report_calendar_copyable_code.md'
$textPath = Join-Path $projectRoot 'report_calendar_copyable_code.txt'

# Include source roots explicitly; never recurse into build caches or signing directories.
$rootNames = @(
    'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat',
    'firebase.json', 'firestore.rules', 'test-firestore-rules.ps1',
    'README.md', 'AUTH_QA.md', 'FIREBASE_LOGIN_SETUP.md', 'FIRESTORE_USERS.md', 'design-qa.md'
)
$files = @(
    foreach ($name in $rootNames) {
        $path = Join-Path $projectRoot $name
        if (Test-Path -LiteralPath $path -PathType Leaf) { Get-Item -LiteralPath $path }
    }
    Get-Item -LiteralPath (Join-Path $projectRoot 'app/build.gradle')
    Get-Item -LiteralPath (Join-Path $projectRoot 'gradle/wrapper/gradle-wrapper.properties')
    Get-ChildItem -LiteralPath (Join-Path $projectRoot 'app/src') -Recurse -File |
        Where-Object { $_.Extension -in @('.java', '.xml') }
) | Sort-Object FullName -Unique

$entries = @(
    foreach ($file in $files) {
        $relative = $file.FullName.Substring($projectRoot.Length + 1).Replace([char]92, [char]47)
        $source = [IO.File]::ReadAllText($file.FullName)
        [pscustomobject]@{ Path = $relative; Content = $source }
    }
)
$lineCount = ($entries | ForEach-Object {
    if ($_.Content.Length -eq 0) { 0 } else {
        ($_.Content -split '\r?\n').Count - [int]$_.Content.EndsWith("`n")
    }
} | Measure-Object -Sum).Sum

$builder = [Text.StringBuilder]::new()
[void]$builder.AppendLine('# 수행평가 캘린더 Android 프로젝트 전체 소스')
[void]$builder.AppendLine()
[void]$builder.AppendLine('생성 시각: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss zzz'))
[void]$builder.AppendLine('포함: ' + $entries.Count + '개 파일, 원본 합계 ' + $lineCount + '줄 (주석·빈 줄·문서 포함)')
[void]$builder.AppendLine()
[void]$builder.AppendLine('최신 Java 소스, 화면 리소스, 빌드 설정, 테스트와 문서를 파일별로 묶었습니다.')
[void]$builder.AppendLine('TXT 파일은 텍스트 편집기에서 Ctrl+A, Ctrl+C로 전체 복사할 수 있습니다.')
[void]$builder.AppendLine('각 파일을 프로젝트로 옮길 때는 경로 제목에 맞게 나누고 코드 블록 표시를 제외해주세요.')
[void]$builder.AppendLine()
[void]$builder.AppendLine('제외: 서명키, 비밀번호 설정, local.properties, google-services.json, IDE/캐시, APK/AAB, 바이너리 파일.')
[void]$builder.AppendLine('이 문서는 소스 공유용이며 완전한 빌드 ZIP이 아닙니다. 다른 PC에서 빌드하려면 Firebase 앱 설정 파일과 Gradle wrapper JAR 등은 별도로 필요합니다.')
[void]$builder.AppendLine()

foreach ($entry in $entries) {
    $language = switch ([IO.Path]::GetExtension($entry.Path)) {
        '.java' { 'java' }
        '.xml' { 'xml' }
        '.gradle' { 'groovy' }
        '.json' { 'json' }
        '.ps1' { 'powershell' }
        '.md' { 'markdown' }
        default { 'text' }
    }
    $maxRun = 0
    foreach ($run in [regex]::Matches($entry.Content, '`+')) {
        $maxRun = [Math]::Max($maxRun, $run.Length)
    }
    $fence = '`' * [Math]::Max(3, $maxRun + 1)
    [void]$builder.AppendLine('## ' + $entry.Path)
    [void]$builder.AppendLine()
    [void]$builder.AppendLine($fence + $language)
    [void]$builder.AppendLine($entry.Content)
    [void]$builder.AppendLine($fence)
    [void]$builder.AppendLine()
}

$result = $builder.ToString()
$utf8 = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText($outputPath, $result, $utf8)
[IO.File]::WriteAllText($textPath, $result, $utf8)

# Verify both exports and every included source before reporting completion.
if ([IO.File]::ReadAllText($outputPath) -cne $result -or [IO.File]::ReadAllText($textPath) -cne $result) {
    throw 'Export readback mismatch'
}
foreach ($entry in $entries) {
    if (-not $result.Contains('## ' + $entry.Path) -or -not $result.Contains($entry.Content)) {
        throw ('Source missing from export: ' + $entry.Path)
    }
}
[pscustomobject]@{
    Files = $entries.Count
    SourceLines = $lineCount
    Markdown = $outputPath
    Text = $textPath
    Verified = $true
}
