@echo off
chcp 65001 >nul
echo ==========================================
echo     Git Upload System for MultiSpace
echo ==========================================
echo.

:: Check if git is available
git --version >nul 2>&1
if errorlevel 1 (
    echo ❌ Git is not installed or not in PATH
    echo Please install Git first
    pause
    exit /b 1
)

:: Check if we're in a git repository
git status >nul 2>&1
if errorlevel 1 (
    echo ❌ This is not a git repository
    echo Please initialize git first with: git init
    pause
    exit /b 1
)

:: Check for changes
echo 🔍 Checking for changes...
git diff --quiet && git diff --cached --quiet
if %errorlevel% equ 0 (
    echo ℹ️  No changes detected
    echo.
    choice /c YN /m "Do you want to continue anyway? (Y/N)"
    if errorlevel 2 (
        echo Operation cancelled
        pause
        exit /b 0
    )
)

:: Show current status
echo.
echo 📋 Current status:
git status --short
echo.

:: Ask for commit message
set /p commit_msg="💬 Enter commit message (or press Enter for auto-generated): "
if "%commit_msg%"=="" (
    for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set mydate=%%c-%%a-%%b
    for /f "tokens=1-2 delims=: " %%a in ('time /t') do set mytime=%%a:%%b
    set commit_msg=Auto commit - %mydate% %mytime%
)

echo.
echo 📦 Adding all changes...
git add .
if errorlevel 1 (
    echo ❌ Failed to add changes
    pause
    exit /b 1
)
echo ✅ Changes added successfully

echo.
echo 💾 Committing changes...
git commit -m "%commit_msg%"
if errorlevel 1 (
    echo ❌ Failed to commit changes
    pause
    exit /b 1
)
echo ✅ Changes committed successfully

echo.
echo 🚀 Pushing to GitHub...
git push
if errorlevel 1 (
    echo ❌ Failed to push to GitHub
    echo.
    echo 💡 Possible solutions:
    echo    1. Check your internet connection
    echo    2. Verify GitHub credentials
    echo    3. Make sure remote origin is set correctly
    echo.
    echo 🔧 To set remote origin:
    echo    git remote add origin https://github.com/username/repository.git
    echo.
    pause
    exit /b 1
)

echo ✅ Successfully pushed to GitHub!
echo.
echo 🎉 Upload completed successfully!
echo    Commit: %commit_msg%
echo.
pause