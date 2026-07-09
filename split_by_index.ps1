$lines = Get-Content "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\MainActivity.kt" -Encoding UTF8
$imports = $lines[0..112] -join "`r`n"
$imports = $imports + "`r`n"

function Write-File($filename, $start, $end) {
    $content = $imports + ($lines[$start..$end] -join "`r`n")
    $path = Join-Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram" $filename
    Set-Content -Path $path -Value $content -Encoding UTF8
    Write-Host "Wrote $filename"
}

# Create folders
New-Item -ItemType Directory -Force -Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\model"
New-Item -ItemType Directory -Force -Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\utils"
New-Item -ItemType Directory -Force -Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\ui\theme"
New-Item -ItemType Directory -Force -Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\ui\components"
New-Item -ItemType Directory -Force -Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\ui\screens"

Write-File "utils\Constants.kt" 113 126
Write-File "model\Models.kt" 127 217
Write-File "ui\theme\RasGramTheme.kt" 218 247
Write-File "utils\AESCrypto.kt" 248 340
# App Entry Point & OTP Login -> Main Activity
# wait, I will handle MainActivity separately
Write-File "ui\screens\MainScreen.kt" 967 1237
Write-File "ui\screens\ChatsTab.kt" 1238 1646
Write-File "ui\screens\ChatArea.kt" 1647 2406
Write-File "ui\components\SwipeToReplyWrapper.kt" 2407 2464
Write-File "ui\components\MessageBubble.kt" 2465 2906
Write-File "ui\screens\StatusTab.kt" 2907 3071
Write-File "ui\screens\CallsTab.kt" 3072 3106
Write-File "ui\screens\GroupsTab.kt" 3107 3192
Write-File "ui\screens\CallingScreen.kt" 3193 3473
Write-File "ui\components\ForwardMessageDialog.kt" 3474 3597
Write-File "ui\components\SettingsDialog.kt" 3598 3728
Write-File "ui\components\NewGroupDialog.kt" 3729 3846
Write-File "ui\components\EmptyState.kt" 3847 3871
Write-File "utils\CloudinaryHelper.kt" 3872 3943
Write-File "utils\FCMHelper.kt" 3944 4036
Write-File "utils\MessageHelper.kt" 4037 4079
Write-File "utils\HelperFunctions.kt" 4080 4148
Write-File "utils\ContactSync.kt" 4149 4200
Write-File "ui\screens\StatusViewer.kt" 4201 4306
Write-File "ui\screens\GroupChatArea.kt" 4307 4710

# Now rewrite MainActivity.kt to ONLY have imports, APP ENTRY POINT, and OTP LOGIN SCREEN
$mainContent = $imports + ($lines[341..966] -join "`r`n")
Set-Content -Path "D:\github web\Project-4-Rasgram\app\src\main\java\com\rasel\rasgram\MainActivity.kt" -Value $mainContent -Encoding UTF8
Write-Host "Wrote MainActivity.kt"
