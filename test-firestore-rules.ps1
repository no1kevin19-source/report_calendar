$ErrorActionPreference = 'Stop'
# Tests run in the Rules evaluator; they do not read or alter actual user documents.
$sessionConfig = Get-Content (Join-Path $env:USERPROFILE '.config/configstore/firebase-tools.json') -Raw | ConvertFrom-Json
$cases = @()
foreach ($method in @('get', 'list', 'delete')) {
    foreach ($identity in @('alice', 'bob', 'anonymous', 'none')) {
        foreach ($target in @('users/alice', 'users/alice/assignments/task1')) {
            $auth = if ($identity -eq 'none') { $null } else {
                @{ uid = $(if ($identity -eq 'anonymous') { 'alice' } else { $identity }); token = @{ email='alice@example.test'; firebase=@{sign_in_provider=$(if ($identity -eq 'anonymous') {'anonymous'} else {'password'})} } }
            }
            $cases += @{expectation=$(if ($identity -eq 'alice' -and $target -eq 'users/alice') {'ALLOW'} else {'DENY'}); request=@{
                path='/databases/(default)/documents/'+$target; method=$method; auth=$auth
            }}
        }
    }
}
foreach ($method in @('create', 'update')) {
    foreach ($variant in @('valid', 'otherOwner', 'wrongEmail', 'adminField', 'missingUid', 'oversizeName')) {
        $data = @{uid='alice';email='alice@example.test';displayName='Alice';updatedAt='2026-09-30T00:00:00Z'}
        if ($variant -eq 'otherOwner') { $data.uid='bob' }
        if ($variant -eq 'wrongEmail') { $data.email='bob@example.test' }
        if ($variant -eq 'adminField') { $data.admin=$true }
        if ($variant -eq 'missingUid') { $data.Remove('uid') }
        if ($variant -eq 'oversizeName') { $data.displayName='x'*257 }
        $cases += @{expectation=$(if ($variant -eq 'valid') {'ALLOW'} else {'DENY'}); request=@{
            path='/databases/(default)/documents/users/alice';method=$method;time='2026-09-30T00:00:00Z';
            auth=@{uid='alice';token=@{email='alice@example.test';firebase=@{sign_in_provider='password'}}};
            resource=@{data=$data}
        }; resource=@{data=@{uid='alice'}}}
    }
}
$body = @{ source=@{files=@(@{name='firestore.rules';content=(Get-Content (Join-Path $PSScriptRoot 'firestore.rules') -Raw)})}; testSuite=@{testCases=$cases} } | ConvertTo-Json -Depth 30
$result = Invoke-RestMethod -Method Post -Uri 'https://firebaserules.googleapis.com/v1/projects/project-771b5:test' -ContentType 'application/json' -Headers @{Authorization='Bearer '+$sessionConfig.tokens.access_token} -Body ([Text.Encoding]::UTF8.GetBytes($body))
$failures = @($result.testResults | Where-Object state -ne 'SUCCESS')
if ($failures.Count -gt 0 -or $result.testResults.Count -ne $cases.Count) {
    $result | ConvertTo-Json -Depth 15
    throw 'Rules tests failed'
}
Write-Output ('PASS: '+$cases.Count+' ownership tests (self, other account, anonymous, unauthenticated).')
