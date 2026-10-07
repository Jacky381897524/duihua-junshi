import concurrent.futures, time, urllib.request
urls = [
    'https://dl.google.com/android/repository/build-tools_r35_windows.zip',
    'https://downloads.gradle.org/distributions/gradle-8.9-bin.zip',
    'https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip',
    'https://aka.ms/download-jdk/microsoft-jdk-17.0.20.1-windows-x64.zip',
    'https://repo.maven.apache.org/maven2/com/google/code/gson/gson/2.11.0/gson-2.11.0.jar',
]
def probe(u):
    start = time.monotonic()
    try:
        with urllib.request.urlopen(urllib.request.Request(u, method='HEAD'), timeout=15) as r:
            print(r.status, r.headers.get('Content-Length'), u.split('/')[2], round(time.monotonic()-start, 1), flush=True)
    except Exception as e: print(u.split('/')[2], type(e).__name__, str(e), flush=True)
with concurrent.futures.ThreadPoolExecutor(5) as pool: list(pool.map(probe, urls))
