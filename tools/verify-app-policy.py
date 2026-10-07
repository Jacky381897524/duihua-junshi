"""Check packaged manifest inputs that protect local credentials and conversations."""
from pathlib import Path
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1] / 'android/app/src/main'
android = '{http://schemas.android.com/apk/res/android}'
app = ET.parse(root / 'AndroidManifest.xml').getroot().find('application')
assert app.get(android + 'allowBackup') == 'false'
assert app.get(android + 'fullBackupContent') == 'false'
assert app.get(android + 'dataExtractionRules') == '@xml/data_extraction_rules'
rules = ET.parse(root / 'res/xml/data_extraction_rules.xml').getroot()
for mode in ('cloud-backup', 'device-transfer'):
    section = rules.find(mode)
    assert section is not None and not section.findall('include')
    for domain in ('root', 'file', 'database', 'sharedpref', 'external'):
        assert any(e.get('domain') == domain and e.get('path') == '.' for e in section.findall('exclude')), (mode, domain)
print('Backup and device-transfer policy: PASS')
