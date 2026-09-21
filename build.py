import os, subprocess, shutil, zipfile

prj_dir = os.path.dirname(os.path.abspath(__file__))
gen_dir = os.path.join(prj_dir, 'gen')
bin_dir = os.path.join(prj_dir, 'bin')
os.makedirs(gen_dir, exist_ok=True)
if os.path.exists(bin_dir):
    shutil.rmtree(bin_dir)
os.makedirs(bin_dir, exist_ok=True)

aapt = r'C:\tools\android-sdk\build-tools\34.0.0\aapt.exe'
d8 = r'C:\tools\android-sdk\build-tools\34.0.0\d8.bat'
apksigner = r'C:\tools\android-sdk\build-tools\34.0.0\apksigner.bat'
zipalign = r'C:\tools\android-sdk\build-tools\34.0.0\zipalign.exe'
android_jar = r'C:\tools\android-sdk\platforms\android-34\android.jar'
javac = r'C:\tools\jdk-install\jdk-17.0.20.1+1\bin\javac.exe'
keytool = r'C:\tools\jdk-install\jdk-17.0.20.1+1\bin\keytool.exe'

manifest = os.path.join(prj_dir, 'AndroidManifest.xml')
res = os.path.join(prj_dir, 'res')

# 1. AAPT
print('1. Running AAPT to generate R.java...')
r1 = subprocess.run([aapt, 'package', '-f', '-m', '-J', gen_dir, '-M', manifest, '-S', res, '-I', android_jar], capture_output=True, text=True)
if r1.returncode != 0:
    print('AAPT error:', r1.stderr)
    exit(1)

# 2. JAVAC
print('2. Compiling Java sources...')
src_files = []
for root, dirs, files in os.walk(os.path.join(prj_dir, 'src')):
    for f in files:
        if f.endswith('.java'):
            src_files.append(os.path.join(root, f))
for root, dirs, files in os.walk(gen_dir):
    for f in files:
        if f.endswith('.java'):
            src_files.append(os.path.join(root, f))

r2 = subprocess.run([javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-bootclasspath', android_jar, '-d', bin_dir] + src_files, capture_output=True)
if r2.returncode != 0:
    print('JAVAC error:', r2.stderr.decode('gbk', errors='replace'))
    exit(1)

# 3. D8
print('3. Running D8 to generate DEX...')
class_files = []
for root, dirs, files in os.walk(bin_dir):
    for f in files:
        if f.endswith('.class'):
            class_files.append(os.path.join(root, f))

r3 = subprocess.run([d8, '--min-api', '10', '--lib', android_jar, '--output', bin_dir] + class_files, shell=True, capture_output=True, encoding='utf-8', errors='replace')
if r3.returncode != 0:
    print('D8 error:', r3.stderr)
    exit(1)

# 4. Package APK
print('4. Packaging APK resources...')
unsigned_apk = os.path.join(bin_dir, 'unsigned.apk')
r4 = subprocess.run([aapt, 'package', '-f', '-M', manifest, '-S', res, '-I', android_jar, '-F', unsigned_apk], capture_output=True, encoding='utf-8', errors='replace')
if r4.returncode != 0:
    print('Package error:', r4.stderr)
    exit(1)

# 5. Add classes.dex
with zipfile.ZipFile(unsigned_apk, 'a') as z:
    z.write(os.path.join(bin_dir, 'classes.dex'), 'classes.dex')

# 6. Zipalign
aligned_apk = os.path.join(bin_dir, 'aligned.apk')
subprocess.run([zipalign, '-f', '4', unsigned_apk, aligned_apk], capture_output=True)

# 7. Keystore & Sign
keystore_path = os.path.join(prj_dir, 'debug.keystore')
if not os.path.exists(keystore_path):
    subprocess.run([keytool, '-genkeypair', '-v', '-keystore', keystore_path, '-alias', 'debug', '-keyalg', 'RSA', '-keysize', '2048', '-validity', '10000', '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=Android,O=Android,C=US'], capture_output=True)

final_apk = os.path.join(prj_dir, 'ChaChaAI.apk')
r7 = subprocess.run([apksigner, 'sign', '--ks', keystore_path, '--ks-key-alias', 'debug', '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--min-sdk-version', '10', '--v1-signing-enabled', 'true', '--v2-signing-enabled', 'false', '--out', final_apk, aligned_apk], shell=True, capture_output=True, encoding='utf-8', errors='replace')

if r7.returncode != 0:
    print('Sign error:', r7.stderr)
    exit(1)

print('BUILD SUCCESS! Final APK size:', os.path.getsize(final_apk), 'bytes at', final_apk)

