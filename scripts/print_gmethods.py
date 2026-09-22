import urllib.request, json, base64

d = json.loads(urllib.request.urlopen('https://api.github.com/repos/DataBackups/PinyinIme/contents/jni/android/com_gaoxin_guangsuime_PinyinDecoderService.cpp').read())
c = base64.b64decode(d['content']).decode('utf-8', errors='ignore')
idx = c.find('JNINativeMethod gMethods')
print(c[idx:idx+1500])
