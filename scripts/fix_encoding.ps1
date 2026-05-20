<#
Scan repository for files with BOM and common mojibake sequences and fix them.
Creates backups with a timestamp suffix before modifying files.
Supported heuristic: when a file decoded as UTF8 contains mojibake tokens (e.g. "�", "–", "’"),
try reinterpreting the raw bytes as Windows-1252 (CP1252) and save as UTF-8 if that removes mojibake.
#>

param(
    [string]$RootPath = ".",
    [string]$BackupSuffix = ".bak.fixencoding_$(Get-Date -Format yyyyMMddHHmmss)"
)

$textExts = @(
    '.java','.js','.ts','.json','.md','.txt','.xml','.html','.htm','.css',
    '.yml','.yaml','.properties','.sql','.ps1','.py','.gradle','.pom','.cs',
    '.c','.cpp','.h','.sh','.bat','.psm1','.psd1','.jsx','.tsx','.cfg','.ini'
)

$log = [System.IO.Path]::Combine($RootPath, 'scripts','fix_encoding_report.txt')
New-Item -Path $log -ItemType File -Force | Out-Null

function Log($s){ Add-Content -Path $log -Value $s; Write-Output $s }

Log "Starting encoding fix run at $(Get-Date)"

$files = Get-ChildItem -Path $RootPath -Recurse -File -ErrorAction SilentlyContinue | Where-Object { $textExts -contains $_.Extension.ToLower() }

$patterns = '\u00C3|\u00E2|\u00C2'

$changed = 0

foreach($f in $files){
    try{
        $bytes = [System.IO.File]::ReadAllBytes($f.FullName)
        if($bytes.Length -eq 0){ continue }

        $madeChange = $false

        # Detect UTF-8 BOM (EF BB BF)
        if($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF){
            $bak = $f.FullName + $BackupSuffix
            Copy-Item -Path $f.FullName -Destination $bak -Force
            $newBytes = $bytes[3..($bytes.Length - 1)]
            [System.IO.File]::WriteAllBytes($f.FullName, $newBytes)
            Log "Removed UTF-8 BOM: $($f.FullName) -> backup $bak"
            $madeChange = $true
        }

        # Read as UTF8 to inspect for mojibake tokens
        $origText = [System.Text.Encoding]::UTF8.GetString($bytes)
        if($origText -match $patterns){
            # Try reinterpreting raw bytes as Windows-1252
            $candidate = [System.Text.Encoding]::GetEncoding(1252).GetString($bytes)

            # Heuristic: candidate should remove mojibake tokens and contain non-ascii chars
            $candidateRemoves = -not ($candidate -match $patterns)
            $candidateHasUnicode = ($candidate -match '[^\u0000-\u007F]')

            if($candidateRemoves -and $candidateHasUnicode){
                $bak = $f.FullName + $BackupSuffix
                Copy-Item -Path $f.FullName -Destination $bak -Force
                [System.IO.File]::WriteAllText($f.FullName, $candidate, [System.Text.Encoding]::UTF8)
                Log "Fixed mojibake (CP1252 -> UTF8) : $($f.FullName) -> backup $bak"
                $madeChange = $true
            } else {
                Log "Detected mojibake-like tokens but heuristic did not improve: $($f.FullName)"
            }
        }

        if($madeChange){ $changed++ }
    } catch {
        Log "ERROR processing $($f.FullName): $($_.Exception.Message)"
    }
}

Log "Completed run at $(Get-Date). Files changed: $changed"
Write-Output "Report written to $log"
