"""Aiwa (qualité max): the 3D plates of the film, in the dark neon studio: P, a phone standing on a black glass floor (the screen is BLACK in the plate: the live screen is laid on it by the film); T, a television (its screen black too);
G, a pair of round spectacles. Cycles on the CPU, denoised. Each plate writes plate-<S>.png and corners-<S>.json (the corners of the screen, or the box of the lenses, in the picture) next to it.
  /tmp/bpyenv/bin/python docs/videos/aiwa-os/plates.py -- --ph /tmp/ph --shot P --out /tmp/os/plates [--width 2560 --height 1440 --samples 64]"""
import os, sys, math, json, zlib, struct
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit
from bpy_extras.object_utils import world_to_camera_view

ph = kit.args('ph', '/tmp/ph'); SHOT = kit.args('shot', 'P'); OUT = kit.args('out', '/tmp/os/plates')
os.makedirs(OUT, exist_ok=True)
def black_png(path, w=64, h=128):
    raw = b''.join(b'\x00' + b'\x05\x06\x0c' * w for _ in range(h))
    def chunk(t, d): return struct.pack('>I', len(d)) + t + d + struct.pack('>I', zlib.crc32(t + d) & 0xffffffff)
    open(path, 'wb').write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(raw)) + chunk(b'IEND', b''))
[black_png(os.path.join(OUT, f'bp_{i}.png')) for i in range(3)]
kit.reset(transparent=False, fps=24)
s = bpy.context.scene
kit.hdri(os.path.join(ph, 'ph-neon_photostudio', 'neon_photostudio_2k.hdr'), strength=0.55, rotation=math.radians(40), visible=False, backdrop='#04050c')

def dim(mat, spec):
    """Less mirror, more matt: a black glass that does not show the whole studio."""
    p = mat.node_tree.nodes['Principled BSDF']
    for name in ('Specular IOR Level', 'Specular'):
        if name in p.inputs: p.inputs[name].default_value = spec
    return mat
def floor():
    bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 0, 0)); f = bpy.context.active_object
    f.data.materials.append(dim(kit.material('black-glass', '#04050a', metallic=0.0, rough=0.22), 0.35))
    return f
def rim(loc, energy, colour, size=1.2, target=(0, 0, 0.1)):
    bpy.ops.object.light_add(type='AREA', location=loc); l = bpy.context.active_object; l.data.energy = energy; l.data.size = size; l.data.color = kit.rgba(colour)[:3]
    tg = mathutils.Vector(target) - mathutils.Vector(loc); l.rotation_euler = tg.to_track_quat('-Z', 'Y').to_euler()
    return l

if SHOT == 'P':
    floor()
    W_, H_, D_ = 0.0715, 0.1470, 0.0082
    phone = kit.phone(os.path.join(OUT, 'bp_%d.png'), frames=1, width=W_, height=H_, depth=D_, corner=0.011, location=(0, 0, 0))
    for poly in phone.data.polygons: poly.use_smooth = (poly.material_index == 1)
    phone.rotation_euler = (math.radians(7), 0, math.radians(-22)); phone.location = (0, 0, H_ / 2 * math.cos(math.radians(7)) + 0.001)
    rim((-0.45, 0.3, 0.22), 40, '#6b9bf5', 0.35, (0, 0, 0.08)); rim((0.5, 0.25, 0.2), 40, '#a77bd6', 0.35, (0, 0, 0.08)); rim((0.0, -0.7, 0.55), 18, '#ffffff', 0.5, (0, 0, 0.08))
    CAMP = ((0.10, -0.95, 0.09), (0.0, 0.0, 0.074), 85.0, 4.0)
elif SHOT == 'T':
    floor()
    TVZ = 0.42
    bpy.ops.mesh.primitive_cube_add(size=1); body = bpy.context.active_object; body.scale = (1.3, 0.04, 0.74); body.location = (0, 0, TVZ + 0.06); body.data.materials.append(kit.material('tv-body', '#07080e', metallic=0.6, rough=0.3))
    bpy.ops.mesh.primitive_cube_add(size=1); neck = bpy.context.active_object; neck.scale = (0.12, 0.05, 0.12); neck.location = (0, 0.02, 0.1); neck.data.materials.append(kit.material('tv-neck', '#0b0c14', metallic=0.7, rough=0.3))
    bpy.ops.mesh.primitive_cube_add(size=1); foot = bpy.context.active_object; foot.scale = (0.7, 0.3, 0.02); foot.location = (0, 0.03, 0.01); foot.data.materials.append(kit.material('tv-foot', '#0b0c14', metallic=0.7, rough=0.3))
    bpy.ops.mesh.primitive_plane_add(size=1); scr = bpy.context.active_object; scr.rotation_euler = (math.radians(90), 0, 0); scr.scale = (1.26, 0.70, 1.0); scr.location = (0, -0.0205, TVZ + 0.06)
    scr.data.materials.append(dim(kit.material('tv-screen-off', '#010103', metallic=0.0, rough=0.2), 0.25))
    rim((-1.6, 0.9, 0.8), 200, '#6b9bf5', 0.8, (0, 0, 0.5)); rim((1.6, 0.9, 0.8), 200, '#a77bd6', 0.8, (0, 0, 0.5)); rim((0.0, -2.2, 1.4), 60, '#ffffff', 0.8, (0, 0, 0.5))
    CAMP = ((0.45, -2.6, 0.62), (0.0, 0.0, 0.48), 42.0, 5.6)
else:
    path = os.path.join(ph, 'ph-round_spectacles', 'round_spectacles_2k.gltf')
    g = kit.import_asset(path, height=None, location=(0, 0, 0.0), yaw=0.0)
    lo, hi = kit.bounds(g); size = hi - lo
    k = 0.15 / max(size.x, size.y, size.z); g.scale = (k, k, k); bpy.context.view_layer.update()
    lo, hi = kit.bounds(g); centre = (lo + hi) / 2; g.location = (g.location.x - centre.x, g.location.y - centre.y, g.location.z - centre.z + 0.1)
    g.rotation_euler = (math.radians(8), 0, math.radians(-32)); bpy.context.view_layer.update()
    floor()
    rim((-0.7, 0.2, 0.4), 50, '#6b9bf5', 0.5, (0, 0, 0.1)); rim((0.7, 0.2, 0.35), 50, '#a77bd6', 0.5, (0, 0, 0.1)); rim((0.0, -0.7, 0.5), 20, '#ffffff', 0.5, (0, 0, 0.1))
    CAMP = ((0.0, -0.62, 0.14), (0.0, 0.0, 0.10), 70.0, 2.8)

bpy.ops.object.camera_add(location=CAMP[0]); cam = bpy.context.active_object; s.camera = cam
tgt = bpy.data.objects.new('target', None); bpy.context.collection.objects.link(tgt); tgt.location = CAMP[1]
tc = cam.constraints.new('TRACK_TO'); tc.target = tgt; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'
cam.data.lens = CAMP[2]; cam.data.dof.use_dof = True; cam.data.dof.aperture_fstop = CAMP[3]; cam.data.dof.focus_object = tgt
bpy.context.view_layer.update()
s.frame_set(0); s.render.filepath = os.path.join(OUT, f'plate-{SHOT}.png'); bpy.ops.render.render(write_still=True)
out = {}
if SHOT == 'P':
    pc = [world_to_camera_view(s, cam, phone.matrix_world @ mathutils.Vector(v)) for v in ((-W_ / 2, -D_ / 2, H_ / 2), (W_ / 2, -D_ / 2, H_ / 2), (W_ / 2, -D_ / 2, -H_ / 2), (-W_ / 2, -D_ / 2, -H_ / 2))]
    out['screen'] = [[round(q.x, 5), round(1 - q.y, 5)] for q in pc]
elif SHOT == 'T':
    pts = [scr.matrix_world @ mathutils.Vector(v) for v in ((-.5, .5, 0), (.5, .5, 0), (.5, -.5, 0), (-.5, -.5, 0))]
    out['screen'] = [[round(q.x, 5), round(1 - q.y, 5)] for q in [world_to_camera_view(s, cam, p) for p in pts]]
else:
    lo, hi = kit.bounds(g)
    box = [world_to_camera_view(s, cam, mathutils.Vector((x, y, z))) for x in (lo.x, hi.x) for y in (lo.y, hi.y) for z in (lo.z, hi.z)]
    out['box'] = [round(min(q.x for q in box), 5), round(1 - max(q.y for q in box), 5), round(max(q.x for q in box), 5), round(1 - min(q.y for q in box), 5)]
json.dump(out, open(os.path.join(OUT, f'corners-{SHOT}.json'), 'w'))
print('plate', SHOT, out)
