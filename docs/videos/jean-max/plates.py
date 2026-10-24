"""Jean (qualité max): the 3D plates. ONE real room (the lounge of Poly Haven, a low table, a notebook, a plant, a lamp, a phone and a flat TV), seen from four places. Each plate is a still, rendered
big (2560x1440, Cycles on the CPU, denoised): the screens of the phone and of the TV are BLACK in it (their reflections stay), the live screens are laid on them by the film's page, on the four corners that
this script writes next to the picture. The camera never moves in 3D: the film moves it (a scale and a shift of the whole plate), so that nothing slides between a screen and its phone.
  /tmp/bpyenv/bin/python docs/videos/jean-max/plates.py -- --ph /tmp/ph --shot B --out /tmp/jean/plates [--width 2560 --height 1440 --samples 96]"""
import os, sys, math, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit
from bpy_extras.object_utils import world_to_camera_view

ph = kit.args('ph', '/tmp/ph'); SHOT = kit.args('shot', 'B'); OUT = kit.args('out', '/tmp/jean/plates')
kit.reset(transparent=False, fps=24)
s = bpy.context.scene
night = SHOT == 'D'
kit.hdri(os.path.join(ph, 'ph-lythwood_lounge', 'lythwood_lounge_2k.hdr'), strength=0.12 if night else kit.args('exposure', 0.5, float), rotation=math.radians(200), visible=True)
def load(asset, gltf, height=None, loc=(0, 0, 0), yaw=0.0):
    return kit.import_asset(os.path.join(ph, asset, gltf), height=height, location=loc, yaw=yaw)
TZ = 0.45
table = load('ph-WoodenTable_01', 'WoodenTable_01_2k.gltf', height=TZ, loc=(0.25, -1.1, 0))
book = load('ph-binder_notebook', 'binder_notebook_2k.gltf', height=0.03, loc=(-0.12, -1.05, TZ), yaw=math.radians(-14))
plant = load('ph-potted_plant_04', 'potted_plant_04_2k.gltf', height=0.22, loc=(0.62, -0.95, TZ), yaw=math.radians(20))
lamp = load('ph-industrial_pipe_lamp', 'industrial_pipe_lamp_2k.gltf', height=0.45, loc=(1.55, -0.2, 0.0), yaw=math.radians(35))
W_, H_, D_ = 0.0715, 0.1470, 0.0082
phone = kit.phone('/tmp/jean/bp_%d.png', frames=1, width=W_, height=H_, depth=D_, corner=0.011, location=(0, 0, 0))
PHONE_POS = {'A': (0.04, -1.40, -18), 'B': (0.04, -1.40, -28), 'C': (0.04, -1.40, 0), 'D': (0.04, -1.40, -28)}[SHOT]
phone.rotation_euler = (math.radians(-90), 0, math.radians(PHONE_POS[2])); phone.location = (PHONE_POS[0], PHONE_POS[1], TZ + 0.006)
TVZ = 0.72
bpy.ops.mesh.primitive_cube_add(size=1); tvb = bpy.context.active_object; tvb.scale = (1.12, 0.035, 0.64); tvb.location = (0.0, 2.5, TVZ); tvb.data.materials.append(kit.material('tv-body', '#0b0b10', metallic=0.6, rough=0.3))
bpy.ops.mesh.primitive_cube_add(size=1); neck = bpy.context.active_object; neck.scale = (0.09, 0.05, 0.40); neck.location = (0.0, 2.52, 0.22); neck.data.materials.append(kit.material('tv-foot', '#15151b', metallic=0.7, rough=0.3))
bpy.ops.mesh.primitive_cube_add(size=1); foot = bpy.context.active_object; foot.scale = (0.58, 0.26, 0.022); foot.location = (0.0, 2.52, 0.011); foot.data.materials.append(kit.material('tv-foot2', '#15151b', metallic=0.7, rough=0.3))
bpy.ops.mesh.primitive_plane_add(size=1); scr = bpy.context.active_object; scr.rotation_euler = (math.radians(90), 0, 0); scr.scale = (1.09, 0.615, 1.0); scr.location = (0.0, 2.481, TVZ)
scr.data.materials.append(kit.material('tv-screen-off', '#020204', metallic=0.2, rough=0.12))
bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 0, 0)); fl = bpy.context.active_object; fl.is_shadow_catcher = True
def blob(x, y, w, d, a=0.75):
    bpy.ops.mesh.primitive_plane_add(size=1, location=(x, y, 0.003)); b = bpy.context.active_object; b.scale = (w, d, 1); b.visible_shadow = False
    m = bpy.data.materials.new('contact'); m.use_nodes = True; n = m.node_tree; n.nodes.clear()
    tc = n.nodes.new('ShaderNodeTexCoord'); mp = n.nodes.new('ShaderNodeMapping'); mp.inputs['Location'].default_value = (-0.5, -0.5, 0)
    gr = n.nodes.new('ShaderNodeTexGradient'); gr.gradient_type = 'SPHERICAL'
    cr = n.nodes.new('ShaderNodeValToRGB'); cr.color_ramp.elements[0].color = (0, 0, 0, 1); cr.color_ramp.elements[0].position = 0.0; cr.color_ramp.elements[1].color = (1, 1, 1, 1); cr.color_ramp.elements[1].position = 0.5
    inv = n.nodes.new('ShaderNodeMath'); inv.operation = 'SUBTRACT'; inv.inputs[0].default_value = 1.0
    mu = n.nodes.new('ShaderNodeMath'); mu.operation = 'MULTIPLY'; mu.inputs[1].default_value = a
    bl = n.nodes.new('ShaderNodeBsdfDiffuse'); bl.inputs['Color'].default_value = (0, 0, 0, 1); tr = n.nodes.new('ShaderNodeBsdfTransparent'); mx = n.nodes.new('ShaderNodeMixShader'); out = n.nodes.new('ShaderNodeOutputMaterial')
    n.links.new(tc.outputs['Generated'], mp.inputs['Vector']); n.links.new(mp.outputs['Vector'], gr.inputs['Vector']); n.links.new(gr.outputs['Fac'], cr.inputs['Fac'])
    n.links.new(cr.outputs['Color'], inv.inputs[1]); n.links.new(inv.outputs[0], mu.inputs[0]); n.links.new(mu.outputs[0], mx.inputs['Fac'])
    n.links.new(tr.outputs[0], mx.inputs[1]); n.links.new(bl.outputs[0], mx.inputs[2]); n.links.new(mx.outputs[0], out.inputs['Surface'])
    b.data.materials.append(m)
blob(0.0, 2.52, 0.85, 0.5, 0.8); blob(1.55, -0.2, 0.7, 0.7, 0.6); blob(0.25, -1.1, 2.2, 1.3, 0.5)
bpy.ops.object.light_add(type='POINT', location=(1.5, -0.15, 0.55)); l = bpy.context.active_object; l.data.energy = 220 if night else 50; l.data.color = (1.0, 0.8, 0.62); l.data.shadow_soft_size = 0.08
bpy.ops.object.light_add(type='AREA', location=(0.0, 1.2, 0.8)); tvl = bpy.context.active_object; tvl.data.energy = 20 if night else 60; tvl.data.size = 1.2; tvl.data.color = (0.55, 0.7, 1.0); tvl.rotation_euler = (math.radians(90 + 25), 0, 0)
# a soft key from the side for the phone (a window's light), so that its glass catches a long reflection
bpy.ops.object.light_add(type='AREA', location=(-1.3, -1.2, 1.2)); key = bpy.context.active_object; key.data.energy = 25 if night else 70; key.data.size = 0.9; key.data.color = (0.75, 0.82, 1.0)
key.rotation_euler = (math.radians(60), 0, math.radians(-50))
# the camera of each plate: where, at what, what lens, how shallow
CAM = {'A': ((-0.95, -2.25, 0.95), (0.15, 0.2, 0.80), 30.0, 5.6),
       'B': ((0.139, -1.645, 0.833), (0.04, -1.40, 0.46), 55.0, 5.6),
       'C': ((0.04, -1.40, 0.92), (0.04, -1.40, 0.46), 50.0, 8.0),
       'D': ((-0.172, -1.767, 0.721), (0.03, -1.40, 0.47), 58.0, 4.0)}[SHOT]
bpy.ops.object.camera_add(location=CAM[0]); cam = bpy.context.active_object; s.camera = cam
tgt = bpy.data.objects.new('target', None); bpy.context.collection.objects.link(tgt); tgt.location = CAM[1]
tc = cam.constraints.new('TRACK_TO'); tc.target = tgt; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'
cam.data.lens = CAM[2]; cam.data.dof.use_dof = True; cam.data.dof.aperture_fstop = CAM[3]; cam.data.dof.focus_object = tgt
if SHOT == 'C': cam.rotation_euler = (0, 0, 0)
os.makedirs(OUT, exist_ok=True)
bpy.context.view_layer.update()
s.frame_set(0); s.render.filepath = os.path.join(OUT, f'plate-{SHOT}.png'); bpy.ops.render.render(write_still=True)
pts = [scr.matrix_world @ mathutils.Vector(v) for v in ((-.5, .5, 0), (.5, .5, 0), (.5, -.5, 0), (-.5, -.5, 0))]
tv = [world_to_camera_view(s, cam, p) for p in pts]
pc = [world_to_camera_view(s, cam, phone.matrix_world @ mathutils.Vector(v)) for v in ((-W_ / 2, -D_ / 2, H_ / 2), (W_ / 2, -D_ / 2, H_ / 2), (W_ / 2, -D_ / 2, -H_ / 2), (-W_ / 2, -D_ / 2, -H_ / 2))]
json.dump({'phone': [[round(q.x, 5), round(1 - q.y, 5)] for q in pc], 'tv': [[round(q.x, 5), round(1 - q.y, 5)] for q in tv]}, open(os.path.join(OUT, f'corners-{SHOT}.json'), 'w'))
