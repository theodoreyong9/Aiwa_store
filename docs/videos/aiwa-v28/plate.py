"""v28, the plate: the desk in real 3D, seven different camera moves (--shot 0..6); the constellations are projected into this room, over it, in the compositing (make.sh).\nBased on v27 desk-shot.py: one continuous scenic shot in real 3D. A wooden table in a warm room (Poly Haven interior light, CC0), a binder notebook, a pair of round spectacles on it, a plant, a lamp
(Poly Haven models, CC0), and a phone leaning on the notebook, its screen the Aiwa widget then the lock screen. The camera starts on the phone, then glides and pulls focus to the spectacles.
  /tmp/bpyenv/bin/python desk-shot.py -- --out frames/ --ph /tmp/ph --screen scr/%04d.jpg --frames 100 [--still 40 --width 960 --height 540 --samples 32]"""
import os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit

ph = kit.args('ph', '/tmp/ph')
N = kit.args('frames', 100, int)
kit.reset(transparent=False)
s = bpy.context.scene
kit.hdri(os.path.join(ph, 'ph-lythwood_lounge', 'lythwood_lounge_2k.hdr'), strength=0.85, rotation=math.radians(200), visible=True)
# low key: the room lights the scene as before, but the camera sees it 12 times darker (a dark, warm, out-of-focus room behind the desk)
_nt = s.world.node_tree; _bg = _nt.nodes['Background']; _env = [n for n in _nt.nodes if n.type == 'TEX_ENVIRONMENT'][0]
_dim = _nt.nodes.new('ShaderNodeBackground'); _dim.inputs['Strength'].default_value = 0.035; _nt.links.new(_env.outputs['Color'], _dim.inputs['Color'])
_ray = _nt.nodes.new('ShaderNodeLightPath'); _mix = _nt.nodes.new('ShaderNodeMixShader')
_nt.links.new(_ray.outputs['Is Camera Ray'], _mix.inputs['Fac']); _nt.links.new(_bg.outputs['Background'], _mix.inputs[1]); _nt.links.new(_dim.outputs['Background'], _mix.inputs[2])
_nt.links.new(_mix.outputs['Shader'], _nt.nodes['World Output'].inputs['Surface'])

def load(asset, gltf, height=None, loc=(0, 0, 0), yaw=0.0):
    return kit.import_asset(os.path.join(ph, asset, gltf), height=height, location=loc, yaw=yaw)

# the table: a plane of wood, 1.2 m wide (the model, set at 0.74 m)
table = load('ph-WoodenTable_01', 'WoodenTable_01_2k.gltf', height=0.74, loc=(0, 0, 0))
top_z = 0.74
book = load('ph-binder_notebook', 'binder_notebook_2k.gltf', height=0.035, loc=(0.02, -0.02, top_z), yaw=math.radians(-12))
spec = load('ph-round_spectacles', 'round_spectacles_2k.gltf', height=0.045, loc=(0.26, -0.03, top_z + 0.036), yaw=math.radians(-28))
plant = load('ph-potted_plant_04', 'potted_plant_04_2k.gltf', height=0.16, loc=(0.52, 0.20, top_z), yaw=math.radians(20))
lamp = load('ph-industrial_pipe_lamp', 'industrial_pipe_lamp_2k.gltf', height=0.42, loc=(-0.45, 0.22, top_z), yaw=math.radians(35))

# the phone: leaning on the book (a block hidden behind holds it), screen toward the camera, a little turned
phone = kit.phone(kit.args('screen'), width=0.0715, height=0.1470, depth=0.0082, corner=0.011, location=(0, 0, 0))
phone.rotation_euler = (math.radians(-22), 0, math.radians(14))        # tilted back, screen up to the camera
phone.location = (-0.15, -0.045, top_z + 0.108)


# a warm lamp light on the scene and a cool rim from the window side, small: the HDRI does the rest
bpy.ops.object.light_add(type='POINT', location=(-0.45, 0.14, top_z + 0.36)); l = bpy.context.active_object; l.data.energy = 70; l.data.color = (1.0, 0.78, 0.55); l.data.shadow_soft_size = 0.05
bpy.ops.object.light_add(type='AREA', location=(0.5, -0.5, top_z + 0.5)); r = bpy.context.active_object; r.data.energy = 40; r.data.size = 0.6; r.data.color = (1.0, 0.45, 0.28)
r.rotation_euler = mathutils.Vector((-0.5, 0.5, -0.7)).to_track_quat('-Z', 'Y').to_euler()
# a cool blue-white spill from where the projection hangs, above the desk: the constellations light the room
bpy.ops.object.light_add(type='POINT', location=(0.05, -0.05, top_z + 0.45)); pj = bpy.context.active_object; pj.data.energy = 18; pj.data.color = (0.55, 0.7, 1.0); pj.data.shadow_soft_size = 0.2

bpy.ops.object.camera_add(); cam = bpy.context.active_object; s.camera = cam
cam.data.lens = kit.args('lens', 40, float); cam.data.dof.use_dof = True; cam.data.dof.aperture_fstop = 2.0
focus = bpy.data.objects.new('focus', None); bpy.context.collection.objects.link(focus); cam.data.dof.focus_object = focus
tc = cam.constraints.new('TRACK_TO'); tc.target = focus; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'
z = top_z
def orbit(t):
    a = math.radians(-70 + 85 * t); c = (0.26 + 0.34 * math.sin(a), -0.03 - 0.34 * math.cos(a), z + 0.10 + 0.04 * t)
    return c, (0.26, -0.03, z + 0.04)
SHOTS = {
    0: lambda t: ((0.10 - 0.05 * t, -1.10 + 0.50 * t, z + 0.22 - 0.03 * t), (0.05, -0.03, z + 0.06)),                       # dolly in from far
    1: orbit,                                                                                                              # orbit round the spectacles
    2: lambda t: ((-0.50 + 1.00 * t, -0.42, z + 0.035), (-0.30 + 1.00 * t, 0.0, z + 0.05)),                              # low truck along the desk
    3: lambda t: ((0.00 + 0.02 * t, -0.30 + 0.15 * t, z + 0.10 + 0.42 * t), (0.0, 0.0, z + 0.04)),                        # crane up over the notebook
    4: lambda t: ((0.05, -0.70, z + 0.16), ((-0.45 + 0.97 * t), 0.20, z + 0.12 + 0.06 * (1 - t))),                         # rack focus lamp to plant
    5: lambda t: ((-0.22 + 0.08 * t, -0.64 + 0.24 * t, z + 0.20 - 0.04 * t), (-0.14, -0.04, z + 0.13)),                    # push in on the phone
    6: lambda t: ((0.0 + 0.1 * t, -0.35 - 0.80 * t, z + 0.12 + 0.20 * t), (0.0, -0.02, z + 0.07)),                         # pull out to the wide
}
k = kit.args('shot', 0, int)
for i in range(N):
    t = i / max(1, N - 1); u = t * t * (3 - 2 * t) if k not in (2, 4) else t
    c, f = SHOTS[k](u)
    c = [c[0] + 0.0012 * math.sin(i * 0.31), c[1], c[2] + 0.0010 * math.sin(i * 0.23 + 1)]     # a hand's breathing
    cam.location = c; cam.keyframe_insert('location', frame=i)
    focus.location = f; focus.keyframe_insert('location', frame=i)
kit.render(kit.args('out', 'frames'))
