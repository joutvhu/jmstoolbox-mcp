#!/usr/bin/env node

const fs = require('fs');
const path = require('path');
const { spawn, execSync } = require('child_process');

function getWindowsDrives() {
    try {
        const output = execSync('fsutil fsinfo drives', { encoding: 'utf8', stdio: ['pipe', 'pipe', 'ignore'] });
        const matches = [...output.matchAll(/\b([A-Za-z]):\\/g)].map(m => m[1].toUpperCase());
        if (matches.length > 0) {
            return matches;
        }
    } catch (e) {}

    // Fallback: Check drive roots C: to Z:
    const drives = [];
    for (let i = 67; i <= 90; i++) {
        const letter = String.fromCharCode(i);
        try {
            if (fs.existsSync(`${letter}:\\`)) {
                drives.push(letter);
            }
        } catch (e) {}
    }
    return drives.length > 0 ? drives : ['C'];
}

function findJavaExecutable() {
    // 1. Check JAVA_HOME
    if (process.env.JAVA_HOME) {
        const rawHome = process.env.JAVA_HOME.replace(/^"(.*)"$/, '$1').trim();
        const binJava = path.join(rawHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java');
        if (fs.existsSync(binJava)) {
            return binJava;
        }
    }

    // 2. Platform-specific JRE detection
    if (process.platform === 'win32') {
        // Query registry on Windows (HKLM, HKCU, and WOW6432Node)
        const registryRoots = [
            'HKLM\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
            'HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall',
            'HKLM\\SOFTWARE\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall'
        ];
        for (const root of registryRoots) {
            try {
                const queryCmd = `reg query "${root}" 2>nul`;
                const output = execSync(queryCmd, { encoding: 'utf8' });
                const jtbKeys = output.split('\n').filter(line => line.toLowerCase().includes('jmstoolbox'));
                for (const key of jtbKeys) {
                    const trimmedKey = key.trim();
                    if (!trimmedKey) continue;
                    try {
                        const locOutput = execSync(`reg query "${trimmedKey}" /v InstallLocation 2>nul`, { encoding: 'utf8' });
                        const match = locOutput.match(/InstallLocation\s+REG_SZ\s+(.*)/i);
                        if (match && match[1]) {
                            const installPath = match[1].trim();
                            const regJava = path.join(installPath, 'jre', 'bin', 'java.exe');
                            if (fs.existsSync(regJava)) return regJava;
                        }
                    } catch (e) {}
                }
            } catch (e) {}
        }

        const standardProgramDirs = [
            process.env.ProgramFiles,
            process.env['ProgramFiles(x86)'],
            process.env.ProgramW6432
        ].filter(Boolean);

        for (const progDir of standardProgramDirs) {
            const p = path.join(progDir, 'JMSToolBox', 'jre', 'bin', 'java.exe');
            if (fs.existsSync(p)) return p;
        }

        const drives = getWindowsDrives();
        for (const drive of drives) {
            const p1 = `${drive}:\\Program Files\\JMSToolBox\\jre\\bin\\java.exe`;
            if (fs.existsSync(p1)) return p1;
            const p2 = `${drive}:\\Program Files (x86)\\JMSToolBox\\jre\\bin\\java.exe`;
            if (fs.existsSync(p2)) return p2;
            const p3 = `${drive}:\\JMSToolBox\\jre\\bin\\java.exe`;
            if (fs.existsSync(p3)) return p3;
        }

        if (process.env.LOCALAPPDATA) {
            const p1 = path.join(process.env.LOCALAPPDATA, 'Programs', 'JMSToolBox', 'jre', 'bin', 'java.exe');
            if (fs.existsSync(p1)) return p1;
            const p2 = path.join(process.env.LOCALAPPDATA, 'JMSToolBox', 'jre', 'bin', 'java.exe');
            if (fs.existsSync(p2)) return p2;
        }
    } else if (process.platform === 'darwin') {
        const userHome = process.env.HOME || '';
        const macLocations = [
            '/Applications/JMSToolBox.app/Contents/Eclipse/jre/Home/bin/java',
            '/Applications/JMSToolBox.app/Contents/Eclipse/jre/bin/java',
            '/Applications/JMSToolBox.app/Contents/jre/bin/java',
            path.join(userHome, 'Applications/JMSToolBox.app/Contents/Eclipse/jre/Home/bin/java'),
            path.join(userHome, 'Applications/JMSToolBox.app/Contents/jre/bin/java'),
            '/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home/bin/java',
            '/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java'
        ];
        for (const loc of macLocations) {
            if (fs.existsSync(loc)) return loc;
        }
    } else {
        const userHome = process.env.HOME || '';
        const linuxLocations = [
            '/opt/jmstoolbox/jre/bin/java',
            '/opt/JMSToolBox/jre/bin/java',
            '/usr/local/jmstoolbox/jre/bin/java',
            '/usr/share/jmstoolbox/jre/bin/java',
            path.join(userHome, 'jmstoolbox/jre/bin/java'),
            path.join(userHome, '.local/share/jmstoolbox/jre/bin/java'),
            '/usr/lib/jvm/default-java/bin/java',
            '/usr/lib/jvm/java-17-openjdk/bin/java',
            '/usr/lib/jvm/java-21-openjdk/bin/java'
        ];
        for (const loc of linuxLocations) {
            if (fs.existsSync(loc)) return loc;
        }
    }

    // 3. Fallback to system PATH
    return 'java';
}

function findJarPath() {
    const candidates = [
        path.join(__dirname, '..', 'jmstoolbox-mcp.jar'),
        path.join(__dirname, '..', 'target', 'jmstoolbox-mcp.jar'),
        path.join(__dirname, 'jmstoolbox-mcp.jar')
    ];

    for (const p of candidates) {
        if (fs.existsSync(p)) {
            return p;
        }
    }
    return null;
}

const javaBin = findJavaExecutable();
const jarPath = findJarPath();

if (!jarPath) {
    console.error('[jmstoolbox-mcp] FATAL: Could not locate jmstoolbox-mcp.jar.');
    console.error('[jmstoolbox-mcp] Checked locations:');
    console.error('  - ' + path.join(__dirname, '..', 'jmstoolbox-mcp.jar'));
    console.error('  - ' + path.join(__dirname, '..', 'target', 'jmstoolbox-mcp.jar'));
    console.error('Please run "mvn clean package" first or check package installation.');
    process.exit(1);
}

const args = ['-jar', jarPath, ...process.argv.slice(2)];

const child = spawn(javaBin, args, {
    stdio: ['pipe', 'pipe', 'inherit'] // pipe stdin & stdout for MCP JSON-RPC, stderr to parent for logs
});

child.on('error', (err) => {
    console.error(`[jmstoolbox-mcp] Failed to launch Java process (${javaBin}): ${err.message}`);
    process.exit(1);
});

child.on('exit', (code, signal) => {
    process.exit(code !== null ? code : 1);
});

process.stdin.pipe(child.stdin);
child.stdout.pipe(process.stdout);

process.on('SIGINT', () => child.kill('SIGINT'));
process.on('SIGTERM', () => child.kill('SIGTERM'));
