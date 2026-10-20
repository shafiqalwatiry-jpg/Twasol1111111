import os
import struct
import zlib
import math

def make_png(width, height, pixel_fn):
    raw_data = bytearray()
    for y in range(height):
        raw_data.append(0) # filter type None
        for x in range(width):
            r, g, b, a = pixel_fn(x, y, width, height)
            raw_data.extend((r, g, b, a))
            
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)

    png = b'\x89PNG\r\n\x1a\n'
    png += chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    png += chunk(b'IDAT', zlib.compress(bytes(raw_data), 9))
    png += chunk(b'IEND', b'')
    return png

def render_tawasol_icon(x, y, w, h, is_round):
    # Normalized coords [0, 1]
    nx = x / float(w)
    ny = y / float(h)
    
    cx, cy = 0.5, 0.5
    dx, dy = nx - cx, ny - cy
    dist = math.sqrt(dx*dx + dy*dy)
    
    # Check bounds
    if is_round:
        if dist > 0.48:
            return (0, 0, 0, 0) # transparent outside circle
    else:
        # Rounded rect with radius 0.22
        r = 0.22
        # Check corners
        in_corner = False
        corner_dist = 0.0
        if nx < r and ny < r:
            corner_dist = math.hypot(nx - r, ny - r)
            in_corner = True
        elif nx > (1 - r) and ny < r:
            corner_dist = math.hypot(nx - (1 - r), ny - r)
            in_corner = True
        elif nx < r and ny > (1 - r):
            corner_dist = math.hypot(nx - r, ny - (1 - r))
            in_corner = True
        elif nx > (1 - r) and ny > (1 - r):
            corner_dist = math.hypot(nx - (1 - r), ny - (1 - r))
            in_corner = True
        if in_corner and corner_dist > r:
            return (0, 0, 0, 0)
            
    # Background gradient: from (0, 168, 132) top-left to (7, 94, 84) bottom-right
    t = (nx + ny) / 2.0
    bg_r = int(0 * (1 - t) + 7 * t)
    bg_g = int(168 * (1 - t) + 94 * t)
    bg_b = int(132 * (1 - t) + 84 * t)
    
    # White Speech Bubble
    # Body: center (0.50, 0.46), width 0.56, height 0.44, corner radius 0.14
    bw, bh = 0.56, 0.42
    bcx, bcy = 0.50, 0.46
    bx1, bx2 = bcx - bw/2, bcx + bw/2
    by1, by2 = bcy - bh/2, bcy + bh/2
    br = 0.12
    
    in_bubble = False
    if bx1 <= nx <= bx2 and by1 <= ny <= by2:
        in_corner = False
        cdist = 0.0
        if nx < bx1 + br and ny < by1 + br:
            cdist = math.hypot(nx - (bx1 + br), ny - (by1 + br))
            in_corner = True
        elif nx > bx2 - br and ny < by1 + br:
            cdist = math.hypot(nx - (bx2 - br), ny - (by1 + br))
            in_corner = True
        elif nx < bx1 + br and ny > by2 - br:
            cdist = math.hypot(nx - (bx1 + br), ny - (by2 - br))
            in_corner = True
        elif nx > bx2 - br and ny > by2 - br:
            cdist = math.hypot(nx - (bx2 - br), ny - (by2 - br))
            in_corner = True
        if not (in_corner and cdist > br):
            in_bubble = True
            
    # Tail of speech bubble (bottom-left)
    # Triangle from (0.32, 0.64) to (0.24, 0.76) to (0.42, 0.64)
    if not in_bubble:
        # Check inside tail triangle
        # Points: A(0.32, 0.64), B(0.24, 0.76), C(0.44, 0.64)
        def sign(p1x, p1y, p2x, p2y, p3x, p3y):
            return (p1x - p3x) * (p2y - p3y) - (p2x - p3x) * (p1y - p3y)
        d1 = sign(nx, ny, 0.32, 0.64, 0.24, 0.76)
        d2 = sign(nx, ny, 0.24, 0.76, 0.44, 0.64)
        d3 = sign(nx, ny, 0.44, 0.64, 0.32, 0.64)
        has_neg = (d1 < 0) or (d2 < 0) or (d3 < 0)
        has_pos = (d1 > 0) or (d2 > 0) or (d3 > 0)
        if not (has_neg and has_pos):
            in_bubble = True

    if in_bubble:
        # Inside speech bubble: check if inside the 3 Tawasol communication dots
        # 3 dots at (0.39, 0.46), (0.50, 0.46), (0.61, 0.46) radius 0.038
        dot_rad = 0.038
        d1 = math.hypot(nx - 0.39, ny - 0.46)
        d2 = math.hypot(nx - 0.50, ny - 0.46)
        d3 = math.hypot(nx - 0.61, ny - 0.46)
        
        if d1 <= dot_rad:
            return (13, 148, 136, 255) # TealPrimary
        elif d2 <= dot_rad:
            return (0, 168, 132, 255) # WhatsApp Teal
        elif d3 <= dot_rad:
            return (7, 94, 84, 255) # Deep Emerald
            
        # Bridge smile curve under the dots
        # Parabola or arc between (0.39, 0.54) and (0.61, 0.54) with center dipping to 0.57
        if 0.39 <= nx <= 0.61:
            norm_x = (nx - 0.50) / 0.11 # -1 to 1
            curve_y = 0.54 + 0.035 * (1.0 - norm_x * norm_x)
            if abs(ny - curve_y) < 0.015:
                return (13, 148, 136, 255)
                
        return (255, 255, 255, 255) # Pure white bubble body
        
    return (bg_r, bg_g, bg_b, 255)

densities = {
    'mipmap-mdpi': 48,
    'mipmap-hdpi': 72,
    'mipmap-xhdpi': 96,
    'mipmap-xxhdpi': 144,
    'mipmap-xxxhdpi': 192,
}

base_res = '/app/src/main/res'
for folder, size in densities.items():
    dir_path = os.path.join(base_res, folder)
    os.makedirs(dir_path, exist_ok=True)
    
    # Square launcher
    square_png = make_png(size, size, lambda x, y, w, h: render_tawasol_icon(x, y, w, h, is_round=False))
    with open(os.path.join(dir_path, 'ic_launcher.png'), 'wb') as f:
        f.write(square_png)
        
    # Round launcher
    round_png = make_png(size, size, lambda x, y, w, h: render_tawasol_icon(x, y, w, h, is_round=True))
    with open(os.path.join(dir_path, 'ic_launcher_round.png'), 'wb') as f:
        f.write(round_png)

print("Successfully generated all density launcher PNG icons!")
