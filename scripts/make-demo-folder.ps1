# Builds a harmless demo folder for SortBot with fake files (messy names, duplicates, temp files, old files).
# Run from Command Prompt or PowerShell:
#   powershell -ExecutionPolicy Bypass -File make-demo-folder.ps1

$dest = 'C:\temp\sortbot-demo'

if (Test-Path -LiteralPath $dest) {
    Remove-Item -LiteralPath $dest -Recurse -Force
}
New-Item -ItemType Directory -Path $dest -Force | Out-Null

function New-DemoFile($name, $text) {
    Set-Content -LiteralPath (Join-Path $dest $name) -Value $text
}

# Documents with unclear names (the AI has to read the content)
New-DemoFile 'scan0032.txt' 'Invoice #4821. Amount due: 42.17 CAD. Payment due Nov 30. Thank you for your purchase.'
New-DemoFile 'scan0033.txt' 'T4 statement. Employment income 18,250. Income tax deducted 1,120. Tax year 2025.'
New-DemoFile 'final_v2_REAL.txt' 'Draft of my essay on the history of the printing press. Introduction. The printing press changed how...'
New-DemoFile 'meeting_notes.txt' 'Team meeting Nov 3. Action items: finish the UI, review the report, schedule demo.'
New-DemoFile 'pancakes.txt' 'Pancake recipe: 1 cup flour, 1 egg, 1 cup milk, 2 tbsp sugar. Whisk and fry.'
New-DemoFile 'resume_draft.txt' 'Jane Doe. Software developer. Skills: Java, Python, SQL. Education: BSc Computer Science.'

# Files the built-in rules can classify on their own
New-DemoFile 'Lecture 03 - Databases.txt' 'Lecture 3: normalization, 1NF, 2NF, 3NF and functional dependencies.'
New-DemoFile 'lec01 (3).pdf' 'slides'
New-DemoFile 'receipt_oct.pdf' 'receipt'
New-DemoFile 'hw2.txt' 'Homework 2: write queries for the sample database.'
New-DemoFile 'IMG_4521.jpg' 'fake photo data: beach trip'
New-DemoFile 'Screenshot 2026-11-10.png' 'fake screenshot data: settings window'
New-DemoFile 'setup_installer.exe' 'fake installer data'
New-DemoFile 'photos_backup.zip' 'fake archive data: photo backup'
New-DemoFile 'Main.java' 'class Main {}'

# Cleanup demo: exact duplicates, temporary files, partial downloads, old files
New-DemoFile 'copy1.txt' 'this file is a duplicate'
New-DemoFile 'copy2.txt' 'this file is a duplicate'
New-DemoFile 'download.tmp' 'partial download of a setup file'
New-DemoFile '~$report.docx' 'word temporary lock file'
New-DemoFile 'video.crdownload' 'unfinished video download'
New-DemoFile 'old_notes_2022.txt' 'old notes from a past course'
New-DemoFile 'flyer_2021.png' 'fake flyer image from 2021'
(Get-Item -LiteralPath (Join-Path $dest 'old_notes_2022.txt')).LastWriteTime = [datetime]'2022-03-10'
(Get-Item -LiteralPath (Join-Path $dest 'flyer_2021.png')).LastWriteTime = [datetime]'2021-06-01'

$count = (Get-ChildItem -LiteralPath $dest | Measure-Object).Count
Write-Host "Created $count demo files in $dest"