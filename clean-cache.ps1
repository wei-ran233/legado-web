param(
    [string]$DataDir = "$PSScriptRoot\data",
    [int]$ExpireDays = 7,
    [int]$IntervalHours = 24,
    [switch]$Daemon
)

$OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-CacheClean {
    param([string]$Path, [int]$Days)

    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  Legado 缓存定时维护与清理任务" -ForegroundColor Cyan
    Write-Host "  - 数据目录: $Path"
    Write-Host "  - 过期阈值: 保留 $Days 天以内的有效缓存"
    Write-Host "  - 执行时间: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
    Write-Host "============================================================"

    if (!(Test-Path $Path)) {
        Write-Warning "数据目录不存在: $Path"
        return
    }

    $cutoff = (Get-Date).AddDays(-$Days)
    $imgDir = Join-Path $Path "images"
    $bookDir = Join-Path $Path "book_cache"

    $totalDeleted = 0
    $totalBytes = [int64]0

    # 1. 清理图片缓存
    if (Test-Path $imgDir) {
        Write-Host "[扫描] 正在检查图片缓存 ($imgDir)..."
        $files = Get-ChildItem -Path $imgDir -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
            $_.Length -eq 0 -or $_.LastWriteTime -lt $cutoff
        }
        foreach ($f in $files) {
            $totalBytes += $f.Length
            $totalDeleted++
            Remove-Item -Path $f.FullName -Force -ErrorAction SilentlyContinue
        }
    }

    # 2. 清理正文与漫画缓存文件
    if (Test-Path $bookDir) {
        Write-Host "[扫描] 正在检查正文与漫画缓存 ($bookDir)..."
        $files = Get-ChildItem -Path $bookDir -Recurse -File -ErrorAction SilentlyContinue | Where-Object {
            $_.Length -eq 0 -or $_.LastWriteTime -lt $cutoff
        }
        foreach ($f in $files) {
            $totalBytes += $f.Length
            $totalDeleted++
            Remove-Item -Path $f.FullName -Force -ErrorAction SilentlyContinue
        }

        # 清理已空的书籍缓存目录
        $emptyDirs = Get-ChildItem -Path $bookDir -Directory -Recurse -ErrorAction SilentlyContinue | Where-Object {
            (Get-ChildItem -Path $_.FullName -Recurse -File -ErrorAction SilentlyContinue).Count -eq 0
        }
        foreach ($dir in $emptyDirs) {
            Remove-Item -Path $dir.FullName -Force -Recurse -ErrorAction SilentlyContinue
        }
    }

    $mb = [math]::Round($totalBytes / 1MB, 2)
    Write-Host "[完成] 缓存清理完成，共移除 $totalDeleted 个过期文件，释放空间约 $mb MB。" -ForegroundColor Green
    Write-Host ""
}

# 记录 PID 方便停止
if ($Daemon) {
    $pidFile = Join-Path $DataDir "cleaner.pid"
    if (!(Test-Path $DataDir)) {
        New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
    }
    $PID | Out-File -FilePath $pidFile -Encoding ascii -Force
    Write-Host "[守护进程] 缓存自动清理守护进程已启动 [PID: $PID]，每 $IntervalHours 小时执行一次巡检..." -ForegroundColor Yellow

    while ($true) {
        Invoke-CacheClean -Path $DataDir -Days $ExpireDays
        Start-Sleep -Seconds ($IntervalHours * 3600)
    }
} else {
    Invoke-CacheClean -Path $DataDir -Days $ExpireDays
}
