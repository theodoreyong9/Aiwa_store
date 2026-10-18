"""The 3D objects of the film, as images with a transparent background: a phone (screen on / screen off) and round glasses, each turning a little (a loop of 24 images).
They are drawn by the stage (and taken apart into points by it). Poly Haven models (CC0), lit by a soft studio and two rims.
  /tmp/bpyenv/bin/python sprites.py -- --ph /tmp/ph --out /tmp/v31/sprites --screen-on /tmp/v30/scrraw/00010.jpg [--only phone_on --still 3 --samples 24]"""
import os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit

PH = kit.args('ph', '/tmp/ph'); OUT = kit.args('out', '/tmp/v31/sprites'); ONLY = kit.args('only', None); STILL = kit.args('still', None, int); SAMPLES = kit.args('samples', 24, int)
FRAMES = 24
SPECS = {'phone_on': (640, 1160), 'phone_off': (640, 1160), 'glasses': (1200, 520)}
os.makedirs('/tmp/v31', exist_ok=True)
black = '/tmp/v31/black.jpg'
if not os.path.exists(black):
    img = bpy.data.images.new('b', 8, 8); img.filepath_raw = black; img.file_format = 'JPEG'; img.save()

for name, (w, h) in SPECS.items():
    if ONLY and name != ONLY: continue
    kit.reset(width=w, height=h, samples=SAMPLES, transparent=True)
    s = bpy.context.scene
    # no HDRI: a plain dark world and soft lights only, so that the glass of a phone does not mirror a window
    wn = s.world.node_tree.nodes['Background']; wn.inputs['Color'].default_value = (0.05, 0.05, 0.06, 1); wn.inputs['Strength'].default_value = 1.0
    if name.startswith('phone'):
        screen = kit.args('screen_on', '/tmp/v30/scrraw/00010.jpg') if name == 'phone_on' else black
        obj = kit.phone(screen, frames=1, width=0.0715, height=0.1470, depth=0.0082, corner=0.011, location=(0, 0, 0))
        obj.rotation_euler = (math.radians(90), 0, 0)         # standing, the screen toward -Y... the phone is built facing -Y already: turn it upright toward the camera
        obj.rotation_euler = (0, 0, 0)
        for ms in obj.material_slots:        # the screen is pure light (the picture of the interface, no reflection of the studio): an emission, not a glass
            if ms.material and ms.material.use_nodes and ms.material.name.startswith('screen'):
                t = ms.material.node_tree
                tex = next(n for n in t.nodes if n.type == 'TEX_IMAGE'); out = next(n for n in t.nodes if n.type == 'OUTPUT_MATERIAL')
                em = t.nodes.new('ShaderNodeEmission'); em.inputs['Strength'].default_value = 1.15 if name == 'phone_on' else 0.0
                gl = t.nodes.new('ShaderNodeBsdfGlossy'); gl.inputs['Roughness'].default_value = 0.10; mxs = t.nodes.new('ShaderNodeMixShader'); mxs.inputs['Fac'].default_value = 0.10 if name == 'phone_on' else 0.30
                t.links.new(tex.outputs['Color'], em.inputs['Color']); t.links.new(em.outputs[0], mxs.inputs[1]); t.links.new(gl.outputs[0], mxs.inputs[2]); t.links.new(mxs.outputs[0], out.inputs['Surface'])      # a veil of glass over the light
        height = 0.147; cam_dist = 0.52; look = (0, 0, 0)
    else:
        obj = kit.import_asset(os.path.join(PH, 'ph-round_spectacles', 'round_spectacles_2k.gltf'), height=0.05, location=(0, 0, 0), yaw=0.0)
        rx, ry, rz = [float(v) for v in kit.args('rot', '0,0,0').split(',')]
        obj.rotation_euler = (math.radians(rx), math.radians(ry), math.radians(rz))
        height = 0.06; cam_dist = 0.42; look = (0, 0, 0.025)
    # lights: a soft key, a warm rim from the left, a cool rim from the right
    for loc, energy, col, size in (((0.12, -0.4, 0.35), 60, (1, 0.97, 0.92), 0.6), ((-0.4, -0.05, 0.1), 90, (1.0, 0.7, 0.45), 0.3), ((0.4, -0.05, 0.12), 80, (0.5, 0.7, 1.0), 0.3), ((0.0, -0.25, -0.3), 25, (0.8, 0.85, 1.0), 0.6)):
        bpy.ops.object.light_add(type='AREA', location=loc); l = bpy.context.active_object; l.data.energy = energy; l.data.color = col; l.data.size = size; l.visible_glossy = False      # the lights light the body, they are not mirrored on the glass
        l.rotation_euler = mathutils.Vector(look).__sub__(mathutils.Vector(loc)).to_track_quat('-Z', 'Y').to_euler()
    bpy.ops.object.camera_add(location=(0, -cam_dist, look[2])); cam = bpy.context.active_object; s.camera = cam
    cam.data.lens = 85; cam.rotation_euler = (math.radians(90), 0, 0)
    base = obj.rotation_euler.copy()
    frames = [STILL] if STILL is not None else range(FRAMES)
    os.makedirs(os.path.join(OUT, name), exist_ok=True)
    for f in frames:
        a = f / FRAMES * 2 * math.pi
        if name.startswith('phone'):
            obj.rotation_euler = (base[0] + 0.05 * math.sin(a), base[1], base[2] + 0.34 * math.sin(a))
        else:
            obj.rotation_euler = (base[0] + 0.06 * math.sin(a + 1), base[1], base[2] + 0.30 * math.sin(a))
        s.render.filepath = os.path.join(OUT, name, '%03d.png' % f); bpy.ops.render.render(write_still=True)
