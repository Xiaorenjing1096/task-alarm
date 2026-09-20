#!/usr/bin/env pwsh
# 生成 release 签名密钥，并准备好 GitHub Secrets 需要的值。
#
# 为什么必须有它：AGP 会在每台机器上重新生成调试密钥库。所以如果 release 用调试密钥
# 签名，每次 CI 跑出来的 APK 签名都不一样 —— 用户无法覆盖升级，只能先卸载重装。
# 正式分发必须有**固定**的密钥。
#
# 用法：
#   tools/make-keystore.ps1
#
# 密钥库默认生成在项目**之外**（$HOME\appalarm-signing\），避免手滑提交进仓库。
# 口令由你在运行时自己输入，脚本不会保存它。

param(
    [string]$OutDir = (Join-Path $env:USERPROFILE 'appalarm-signing'),
    [string]$Alias = 'appalarm'
)

$ErrorActionPreference = 'Continue'

# ---------------------------------------------------------------- 找 keytool
function Find-Keytool {
    $candidates = @(
        (Join-Path $env:JAVA_HOME 'bin\keytool.exe'),
        (Join-Path $env:ProgramFiles 'Android\Android Studio\jbr\bin\keytool.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'Android\Android Studio\jbr\bin\keytool.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr\bin\keytool.exe')
    )
    foreach ($drive in (Get-PSDrive -PSProvider FileSystem -ErrorAction SilentlyContinue)) {
        $candidates += (Join-Path $drive.Root 'Program Files\Android\Android Studio\jbr\bin\keytool.exe')
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    return $null
}

$keytool = Find-Keytool
if (-not $keytool) {
    throw '找不到 keytool。请设置 $env:JAVA_HOME 指向一个 JDK，或安装 Android Studio。'
}
Write-Host "使用 keytool: $keytool" -ForegroundColor DarkGray

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$keystore = Join-Path $OutDir 'release.jks'

# ---------------------------------------------------------------- 生成
if (Test-Path $keystore) {
    Write-Host ""
    Write-Host "密钥库已存在，跳过生成：$keystore" -ForegroundColor Yellow
    Write-Host "（要重新生成，请先手动删除它 —— 但注意：换了密钥，已发布的版本就再也无法覆盖升级。）" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "即将生成密钥库：$keystore" -ForegroundColor Cyan
    Write-Host "接下来 keytool 会提示输入两次口令。请自己选一个强口令并**记牢** —— 它无法找回。" -ForegroundColor Cyan
    Write-Host "（PKCS12 密钥库下，密钥口令与库口令相同，所以只会问这一组。）" -ForegroundColor DarkGray
    Write-Host ""

    & $keytool -genkeypair -v `
        -keystore $keystore `
        -alias $Alias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname "CN=AppAlarm, OU=Personal, O=AppAlarm, L=Unknown, ST=Unknown, C=CN"

    if (-not (Test-Path $keystore)) {
        throw 'keytool 没有生成密钥库，请看上面的报错。'
    }
    Write-Host ""
    Write-Host "✅ 密钥库已生成" -ForegroundColor Green
}

# ---------------------------------------------------------------- 导出 base64
$bytes = [IO.File]::ReadAllBytes($keystore)
$base64 = [Convert]::ToBase64String($bytes)
$b64File = Join-Path $OutDir 'keystore.b64'
[IO.File]::WriteAllText($b64File, $base64, (New-Object System.Text.UTF8Encoding $false))

$copied = $true
try { Set-Clipboard -Value $base64 } catch { $copied = $false }

# ---------------------------------------------------------------- 打印下一步
Write-Host ""
Write-Host "==================== 接下来要做的 ====================" -ForegroundColor Cyan
Write-Host ""
Write-Host "1) base64 已写入：$b64File"
if ($copied) {
    Write-Host "   并且已经复制到剪贴板 ✅" -ForegroundColor Green
} else {
    Write-Host "   （复制到剪贴板失败，请手动打开上面这个文件复制）" -ForegroundColor Yellow
}
Write-Host ""
Write-Host "2) 打开 GitHub 仓库 → Settings → Secrets and variables → Actions → New repository secret"
Write-Host "   依次添加这 4 个（名字必须一模一样）："
Write-Host ""
Write-Host "      APPALARM_KEYSTORE_BASE64      ← 粘贴上面那份 base64（整个文件内容）"
Write-Host "      APPALARM_KEYSTORE_PASSWORD    ← 你刚才设的库口令"
Write-Host "      APPALARM_KEY_ALIAS            ← $Alias"
Write-Host "      APPALARM_KEY_PASSWORD         ← 同上，也是你的库口令"
Write-Host ""
Write-Host "3) 然后打 tag 触发自动发布："
Write-Host "      git tag -a v1.0 -m `"v1.0`""
Write-Host "      git push origin v1.0"
Write-Host ""
Write-Host "⚠️  两点提醒" -ForegroundColor Yellow
Write-Host "   · 换成正式密钥后签名会变，你现在手机上装的那份必须**先卸载**，再装新的。之后就正常了。"
Write-Host "   · $keystore 千万不要提交进仓库，也不要丢 —— 丢了就再也无法给这个应用发更新。"
Write-Host "     （它放在项目目录之外，`.gitignore` 里也挡住了 *.jks 双保险。）"
Write-Host ""
