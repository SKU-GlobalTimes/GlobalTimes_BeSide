param(
    [Parameter(Mandatory = $true)]
    [int]$PrNumber,

    [string]$Repo = "SKU-GlobalTimes/GlobalTimes_BeSide"
)

$ErrorActionPreference = "Stop"

function Write-Section {
    param([string]$Title)
    Write-Output ""
    Write-Output $Title
    Write-Output ("-" * $Title.Length)
}

function Get-Section {
    param(
        [string]$Body,
        [string]$Name
    )

    $pattern = "(?ms)^###\s+$([regex]::Escape($Name))\s*(.*?)(?=^###\s+|\z)"
    $match = [regex]::Match($Body, $pattern)
    if (-not $match.Success) {
        return ""
    }

    return $match.Groups[1].Value.Trim()
}

function Test-EmptySection {
    param([string]$Content)

    if ([string]::IsNullOrWhiteSpace($Content)) {
        return $true
    }

    $noneKo = "$([char]0xC5C6)$([char]0xC74C)"
    $normalized = $Content.Trim()
    return $normalized -match "^-?\s*($noneKo|N/A|None|none)\s*\.?$"
}

$json = gh pr view $PrNumber --repo $Repo --comments --json comments,url,state,mergeable | ConvertFrom-Json

$reviewComments = @($json.comments | Where-Object {
        $_.body -match "AI Reviewer" -or $_.body -match "Blocking"
    } | Sort-Object createdAt -Descending)

if ($reviewComments.Count -eq 0) {
    Write-Output "PR #$PrNumber AI Reviewer Status"
    Write-Output "Decision: REVIEW_NOT_FOUND"
    Write-Output "Reason: AI Reviewer comment was not found."
    exit 2
}

$latest = $reviewComments[0]
$body = [string]$latest.body

$blocking = Get-Section -Body $body -Name "Blocking"
$nonBlocking = Get-Section -Body $body -Name "Non-blocking"
$conclusion = Get-Section -Body $body -Name "Conclusion"
if ([string]::IsNullOrWhiteSpace($conclusion)) {
    $conclusionKo = "$([char]0xACB0)$([char]0xB860)"
    $conclusion = Get-Section -Body $body -Name $conclusionKo
}

$hasBlocking = -not (Test-EmptySection -Content $blocking)
$decision = if ($hasBlocking) { "CHANGES_REQUIRED" } else { "MERGE_READY" }

Write-Output "PR #$PrNumber AI Reviewer Status"
Write-Output "PR URL: $($json.url)"
Write-Output "PR State: $($json.state)"
Write-Output "Mergeable: $($json.mergeable)"
Write-Output "Reviewer Comment: $($latest.url)"
Write-Output "Reviewer CreatedAt: $($latest.createdAt)"
Write-Output "Decision: $decision"

Write-Section "Blocking"
if ([string]::IsNullOrWhiteSpace($blocking)) {
    Write-Output "(section not found)"
} else {
    Write-Output $blocking
}

Write-Section "Non-blocking"
if ([string]::IsNullOrWhiteSpace($nonBlocking)) {
    Write-Output "(section not found)"
} else {
    Write-Output $nonBlocking
}

Write-Section "Conclusion"
if ([string]::IsNullOrWhiteSpace($conclusion)) {
    Write-Output "(section not found)"
} else {
    Write-Output $conclusion
}

if ($hasBlocking) {
    exit 1
}

exit 0
