"""The background of the profile: a loop of 6 seconds (seamless), made with Blender (Cycles, on the CPU). A night city (a CC0 panorama of Poly Haven) turns slowly once around the camera while a few pieces of glass,
chrome and light turn in front of it: everything turns by a whole number of turns over the loop, so that the last picture leads into the first.
  /tmp/bpyenv/bin/python docs/videos/profile-loop/loop.py -- --ph /tmp/ph --out /tmp/profile/frames [--width 960 --height 540 --samples 40 --frames 144] [--still 0]"""
import os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy
import kit

ph = kit.args('ph', '/tmp/ph'); OUT = kit.args('out', '/tmp/profile/frames')
kit.reset(width=kit.args('width', 960, int), height=kit.args('height', 540, int), samples=kit.args('samples', 40, int), fps=24, transparent=False)
s = bpy.context.scene
N = kit.args('frames', 144, int)
kit.hdri(os.path.join(ph, 'ph-shanghai_bund', 'shanghai_bund_2k.hdr'), strength=0.65, rotation=0.0)
env_map = [n for n in s.world.node_tree.nodes if n.type == 'MAPPING'][0]

glass = kit.material('glass', '#dfe8ff', glass=True)
glass_blue = kit.material('glass-blue', '#8fb2ff', glass=True)
chrome = kit.material('chrome', '#e8ecff', metallic=1.0, rough=0.06)
violet = kit.material('violet', '#a77bd6', emit='#a77bd6', emit_strength=7.0)
mint = kit.material('mint', '#52d6a0', emit='#52d6a0', emit_strength=6.0)
blue = kit.material('blue', '#6b9bf5', emit='#6b9bf5', emit_strength=7.0)

spin = []          # (object, turns over the loop, axis)
def add(kind, mat, loc, size, turns, axis=2, **kw):
    if kind == 'ico': bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=size, location=loc)
    elif kind == 'torus': bpy.ops.mesh.primitive_torus_add(major_radius=size, minor_radius=size * 0.28, major_segments=64, minor_segments=24, location=loc)
    elif kind == 'octa': bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=size, location=loc)
    elif kind == 'cube': bpy.ops.mesh.primitive_cube_add(size=size, location=loc)
    o = bpy.context.active_object; o.data.materials.append(mat)
    for p in o.data.polygons: p.use_smooth = kind in ('torus',)
    spin.append((o, turns, axis, kw.get('tilt', (0.3, 0.2, 0.0))))
    return o

add('ico', glass, (0.0, 0.0, 0.0), 0.62, 1, 2, tilt=(0.4, 0.1, 0.0))
add('torus', chrome, (0.0, 0.0, 0.0), 1.1, 1, 0, tilt=(1.0, 0.0, 0.4))
add('torus', glass_blue, (0.0, 0.0, 0.0), 1.55, -1, 1, tilt=(0.2, 1.1, 0.0))
add('ico', violet, (2.9, -0.4, 0.7), 0.12, 2, 2); add('ico', blue, (-3.1, 0.2, -0.5), 0.15, 2, 1); add('ico', mint, (1.0, 0.6, -1.6), 0.1, 3, 0)
add('cube', chrome, (-2.2, -1.2, 0.9), 0.3, 1, 2, tilt=(0.5, 0.5, 0.2)); add('cube', glass, (2.4, 0.8, -0.9), 0.36, -1, 1, tilt=(0.3, 0.9, 0.1))
bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1, radius=0.001)       # keeps the file from being empty of scale references

bpy.ops.object.camera_add(location=(0.0, -9.5, 0.4)); cam = bpy.context.active_object; s.camera = cam
cam.data.lens = 38
cam.rotation_euler = (math.radians(89.3), 0, 0)
cam.data.dof.use_dof = True; cam.data.dof.focus_distance = 9.5; cam.data.dof.aperture_fstop = 6.0

os.makedirs(OUT, exist_ok=True)
still = kit.args('still', None, int)
for f in ([still] if still is not None else range(N)):
    path = os.path.join(OUT, '%05d.png' % f)
    if still is None and os.path.exists(path) and os.path.getsize(path) > 0 and not os.path.exists(path + '.part'):
        continue
    t = f / N
    env_map.inputs['Rotation'].default_value[2] = 2 * math.pi * t
    for o, turns, axis, tilt in spin:
        e = list(tilt); e[axis] = e[axis] + 2 * math.pi * turns * t
        o.rotation_euler = e
    if still is None: open(path + '.part', 'w').close()
    s.render.filepath = path; bpy.ops.render.render(write_still=True)
    if still is None: os.remove(path + '.part')
print('frames done', OUT)
