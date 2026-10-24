"""Generate tiny deterministic archives for ZipArchiveTest (Python standard library)."""
from pathlib import Path
import struct
import sys
import zipfile
import zlib

root = Path(sys.argv[1])
root.mkdir(parents=True, exist_ok=True)
payload = bytes(range(256)) * 300
(root / 'expected.bin').write_bytes(payload)
with zipfile.ZipFile(root / 'mixed.zip', 'w') as z:
    z.writestr('说明.txt', '非视频')
    z.writestr('folder/中文视频.MP4', payload, compress_type=zipfile.ZIP_STORED)
    z.writestr('folder/压缩视频.mp4', payload, compress_type=zipfile.ZIP_DEFLATED)
    z.writestr('../../escape.mp4', payload, compress_type=zipfile.ZIP_DEFLATED)
    z.comment = b'comment PK\x05\x06 with false signature'
with zipfile.ZipFile(root / 'empty.zip', 'w') as z:
    z.writestr('readme.txt', 'no video')
data = bytearray((root / 'mixed.zip').read_bytes())
(root / 'truncated.zip').write_bytes(data[:-10])

def one_entry(name, zip64=False, encrypted=False, wrong_crc=False, method=0, wrong_size=False):
    raw_name = name.encode('gb18030')
    flags = 1 if encrypted else 0
    crc = zlib.crc32(payload) ^ (1 if wrong_crc else 0)
    if method == 8:
        compressor = zlib.compressobj(wbits=-15)
        packed = compressor.compress(payload) + compressor.flush()
    else:
        packed = payload
    size = len(payload) + (1 if wrong_size else 0)
    size32, compressed32, offset32 = size, len(packed), 0
    extra = b''
    if zip64:
        size32 = compressed32 = offset32 = 0xffffffff
        extra = struct.pack('<HHQQQ', 1, 24, size, len(packed), 0)
    local_extra = struct.pack('<HHQQ', 1, 16, size, len(packed)) if zip64 else b''
    local = struct.pack('<IHHHHHIIIHH', 0x04034b50, 45 if zip64 else 20, flags, method,
                        0, 0, crc, compressed32, size32, len(raw_name), len(local_extra))
    local += raw_name + local_extra + packed
    central = struct.pack('<IHHHHHHIIIHHHHHII', 0x02014b50, 45, 45 if zip64 else 20,
                          flags, method, 0, 0, crc, compressed32, size32, len(raw_name),
                          len(extra), 0, 0, 0, 0, offset32) + raw_name + extra
    result = local + central
    if zip64:
        eocd64 = struct.pack('<IQHHIIQQQQ', 0x06064b50, 44, 45, 45, 0, 0,
                             1, 1, len(central), len(local))
        result += eocd64 + struct.pack('<IIQI', 0x07064b50, 0, len(local) + len(central), 1)
        result += struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, 65535, 65535, 0xffffffff, 0xffffffff, 0)
    else:
        result += struct.pack('<IHHHHIIH', 0x06054b50, 0, 0, 1, 1, len(central), len(local), 0)
    return result

(root / 'zip64.zip').write_bytes(one_entry('大文件测试.mp4', zip64=True))
(root / 'gbk.zip').write_bytes(one_entry('中文目录/视频.mp4'))
(root / 'encrypted.zip').write_bytes(one_entry('secret.mp4', encrypted=True, method=8))
(root / 'bad-crc.zip').write_bytes(one_entry('broken.mp4', wrong_crc=True, method=8))
(root / 'bad-size.zip').write_bytes(one_entry('broken.mp4', method=8, wrong_size=True))
(root / 'unsupported.zip').write_bytes(one_entry('odd.mp4', method=99))
print('Generated ZIP fixtures in', root)
