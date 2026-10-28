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
            auth=@{uid='alice';token=@{email='alice@example.test';email_verified=$true;firebase=@{sign_in_provider='password'}}};
            resource=@{data=$data}
        }; resource=@{data=@{uid='alice'}}}
    }
}
# Exercise claims independently of document fields and preserve read/delete access.
foreach ($claim in @('verified', 'unverified', 'missing', 'string', 'number')) {
    foreach ($identity in @('alice', 'bob', 'anonymous', 'none')) {
        foreach ($method in @('get', 'list', 'delete', 'create', 'update')) {
            $token = @{email='alice@example.test';firebase=@{sign_in_provider=$(if ($identity -eq 'anonymous') {'anonymous'} else {'password'})}}
            switch ($claim) {
                'verified' { $token.email_verified = $true }
                'unverified' { $token.email_verified = $false }
                'string' { $token.email_verified = 'true' }
                'number' { $token.email_verified = 1 }
            }
            $auth = if ($identity -eq 'none') { $null } else {
                @{uid=$(if ($identity -eq 'anonymous') {'alice'} else {$identity});token=$token}
            }
            $write = $method -in @('create', 'update')
            $allowed = $identity -eq 'alice' -and (-not $write -or $claim -eq 'verified')
            $cases += @{expectation=$(if ($allowed) {'ALLOW'} else {'DENY'}); request=@{
                path='/databases/(default)/documents/users/alice';method=$method;time='2026-10-07T00:00:00Z';auth=$auth;
                resource=@{data=@{uid='alice';email='alice@example.test';displayName='Alice';updatedAt='2026-10-07T00:00:00Z'}}
            };resource=@{data=@{uid='alice'}}}
        }
    }
}
# Synthetic public-assignment requests only; no production documents are accessed.
foreach ($method in @('create','update')) {
    foreach ($identity in @('alice','bob','anonymous','none')) {
        foreach ($variant in @('valid','private','forgedOwner','transferOwner','extraPhoto','missingTitle','longTitle','badDate','badComplete','badTime','badId')) {
            $data = @{id='task';ownerUid='alice';visibility='public';title='Task';subject='Science';period='1';detail='';dueMillis=1;complete=$false;updatedAt='2026-10-07T00:00:00Z'}
            switch ($variant) {
                'private' {$data.visibility='private'}
                'forgedOwner' {$data.ownerUid='other'}
                'transferOwner' {$data.ownerUid='bob'}
                'extraPhoto' {$data.photos=@('content://private/photo')}
                'missingTitle' {$data.Remove('title')}
                'longTitle' {$data.title='x'*201}
                'badDate' {$data.dueMillis=-1}
                'badComplete' {$data.complete='true'}
                'badTime' {$data.updatedAt='2000-01-01T00:00:00Z'}
                'badId' {$data.id='another'}
            }
            $auth = if ($identity -eq 'none') {$null} else {
                @{uid=$(if ($identity -eq 'anonymous') {'alice'} else {$identity});token=@{email_verified=$false;firebase=@{sign_in_provider=$(if ($identity -eq 'anonymous') {'anonymous'} else {'password'})}}}
            }
            $allowed = ($identity -eq 'alice' -and $variant -eq 'valid') -or ($method -eq 'create' -and $identity -eq 'bob' -and $variant -eq 'transferOwner')
            $cases += @{expectation=$(if($allowed){'ALLOW'}else{'DENY'});request=@{path='/databases/(default)/documents/assignments/task';method=$method;auth=$auth;time='2026-10-07T00:00:00Z';resource=@{data=$data}};resource=@{data=@{ownerUid='alice';visibility='public'}}}
        }
    }
}
foreach ($method in @('get','list','delete')) {
    foreach ($identity in @('alice','bob','anonymous','none')) {
        foreach ($visibility in @('public','private')) {
            $auth = if ($identity -eq 'none') {$null} else {
                @{uid=$(if ($identity -eq 'anonymous') {'alice'} else {$identity});token=@{firebase=@{sign_in_provider=$(if ($identity -eq 'anonymous') {'anonymous'} else {'password'})}}}
            }
            $allowed = if ($method -eq 'delete') {$identity -eq 'alice'} else {$identity -in @('alice','bob') -and $visibility -eq 'public'}
            $cases += @{expectation=$(if($allowed){'ALLOW'}else{'DENY'});request=@{path='/databases/(default)/documents/assignments/task';method=$method;auth=$auth};resource=@{data=@{ownerUid='alice';visibility=$visibility}}}
        }
    }
}
$cases += @{expectation='ALLOW';request=@{path='/databases/(default)/documents/assignments/missing';method='get';auth=@{uid='alice';token=@{firebase=@{sign_in_provider='password'}}}}}
foreach ($case in $cases) {
    $case.functionMocks = @(@{function='exists';args=@(@{anyValue=@{}});result=@{value=($case.request.path -ne '/databases/(default)/documents/assignments/missing')}})
}
$body = @{ source=@{files=@(@{name='firestore.rules';content=(Get-Content (Join-Path $PSScriptRoot 'firestore.rules') -Raw)})}; testSuite=@{testCases=$cases} } | ConvertTo-Json -Depth 30
$result = Invoke-RestMethod -Method Post -Uri 'https://firebaserules.googleapis.com/v1/projects/project-771b5:test' -ContentType 'application/json' -Headers @{Authorization='Bearer '+$sessionConfig.tokens.access_token} -Body ([Text.Encoding]::UTF8.GetBytes($body))
$failures = @($result.testResults | Where-Object state -ne 'SUCCESS')
if ($failures.Count -gt 0 -or $result.testResults.Count -ne $cases.Count) {
    for ($i=0; $i -lt $result.testResults.Count; $i++) {
        if ($result.testResults[$i].state -ne 'SUCCESS') {
            @{index=$i;case=$cases[$i];result=$result.testResults[$i]} | ConvertTo-Json -Depth 12
        }
    }
    throw 'Rules tests failed'
}
Write-Output ('PASS: '+$cases.Count+' rule tests (ownership, fields, verified/unverified/missing/invalid claims; read/delete unchanged).')
