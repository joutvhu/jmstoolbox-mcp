const fs = require('fs');
const path = require('path');
const { spawnSync, execSync } = require('child_process');

const rootDir = path.resolve(__dirname, '..');
const isWindows = process.platform === 'win32';
const mvnwCmd = isWindows ? path.join(rootDir, 'mvnw.cmd') : path.join(rootDir, 'mvnw');

console.log('=== [1/3] Building Fat JAR with Maven ===');
let buildRes;
if (fs.existsSync(mvnwCmd)) {
    if (!isWindows) {
        try { fs.chmodSync(mvnwCmd, '755'); } catch (e) {}
    }
    buildRes = isWindows
        ? spawnSync('cmd.exe', ['/c', mvnwCmd, 'clean', 'package', '-DskipTests'], {
              cwd: rootDir,
              stdio: 'inherit'
          })
        : spawnSync(mvnwCmd, ['clean', 'package', '-DskipTests'], {
              cwd: rootDir,
              stdio: 'inherit'
          });
} else {
    // Fallback to system mvn
    const mvn = isWindows ? 'mvn.cmd' : 'mvn';
    buildRes = isWindows
        ? spawnSync('cmd.exe', ['/c', 'mvn', 'clean', 'package', '-DskipTests'], {
              cwd: rootDir,
              stdio: 'inherit'
          })
        : spawnSync('mvn', ['clean', 'package', '-DskipTests'], {
              cwd: rootDir,
              stdio: 'inherit'
          });
}

if (buildRes.status !== 0) {
    console.error('\n[ERROR] Maven build failed with exit code ' + buildRes.status);
    process.exit(1);
}

const targetJar = path.join(rootDir, 'target', 'jmstoolbox-mcp.jar');
const rootJar = path.join(rootDir, 'jmstoolbox-mcp.jar');

if (!fs.existsSync(targetJar)) {
    console.error('\n[ERROR] Target JAR not found at ' + targetJar);
    process.exit(1);
}

console.log('\n=== [2/3] Copying JAR to root for package distribution ===');
fs.copyFileSync(targetJar, rootJar);
console.log('Copied ' + targetJar + ' -> ' + rootJar);

console.log('\n=== [3/3] Installing into global npm directory (npm install -g) ===');

// Automatically stop any lingering instances before reinstalling
try {
    if (isWindows) {
        const script = "Get-CimInstance Win32_Process | Where-Object CommandLine -match 'jmstoolbox-mcp\\.jar' | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }";
        const encoded = Buffer.from(script, 'utf16le').toString('base64');
        execSync('powershell.exe -NoProfile -EncodedCommand ' + encoded, { stdio: 'ignore' });
    } else {
        execSync('pkill -f jmstoolbox-mcp.jar || true', { stdio: 'ignore' });
    }
} catch (e) {}

let tarballPath = '';
try {
    const packOutput = execSync('npm pack', {
        cwd: rootDir,
        encoding: 'utf8'
    }).trim();
    const tarball = packOutput.split('\n').pop().trim();
    tarballPath = path.join(rootDir, tarball);

    const installRes = isWindows
        ? spawnSync('cmd.exe', ['/c', 'npm', 'install', '-g', tarballPath, '--force'], {
              cwd: rootDir,
              stdio: 'inherit'
          })
        : spawnSync('npm', ['install', '-g', tarballPath, '--force'], {
              cwd: rootDir,
              stdio: 'inherit'
          });

    if (installRes.status !== 0) {
        console.error('\n[ERROR] Global npm install failed with exit code ' + installRes.status);
        process.exit(1);
    }
} catch (err) {
    console.error('\n[ERROR] Failed to package and install globally: ' + err.message);
    process.exit(1);
} finally {
    if (tarballPath && fs.existsSync(tarballPath)) {
        try {
            fs.unlinkSync(tarballPath);
        } catch (e) {}
    }
}

let npmGlobalRoot = '';
try {
    npmGlobalRoot = execSync('npm root -g', { encoding: 'utf8' }).trim();
} catch (e) {}

const installedPath = npmGlobalRoot ? path.join(npmGlobalRoot, 'jmstoolbox-mcp') : 'global node_modules';

console.log('\n======================================================');
console.log('SUCCESS! jmstoolbox-mcp has been published to npm -g');
console.log('Global location: ' + installedPath);
