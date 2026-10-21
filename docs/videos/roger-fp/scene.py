"""Roger in the first person: the room seen from the sofa. One continuous 3D scene (Blender, Cycles on the CPU): the lounge of Poly Haven (HDRI « lythwood_lounge », CC0) is the room, in front of it
a low table with a plant, a lamp and a notebook (Poly Haven, CC0), the phone of the kit lying on the table, and a flat TV (a made-up object: a slim box whose screen is an image sequence). The camera is
Roger's eyes: it breathes, turns toward the TV, looks down at the phone and comes back.
  /tmp/bpyenv/bin/python scene.py -- --ph /tmp/ph --out /tmp/rfp/s --tv /tmp/rfp/tv/%04d.png --phone /tmp/rfp/ph/%04d.png --frames 240 [--still 40 --width 960 --height 540 --samples 16]"""
import os, sys, math
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit

ph = kit.args('ph', '/tmp/ph'); FPS = kit.args('fps', 12, int); N = kit.args('frames', 240, int)
kit.reset(transparent=False, fps=FPS)
s = bpy.context.scene
kit.hdri(os.path.join(ph, 'ph-lythwood_lounge', 'lythwood_lounge_2k.hdr'), strength=kit.args('exposure', 0.6, float), rotation=math.radians(kit.args('rot', 200, float)), visible=True)
def load(asset, gltf, height=None, loc=(0, 0, 0), yaw=0.0):
    return kit.import_asset(os.path.join(ph, asset, gltf), height=height, location=loc, yaw=yaw)
# the low table in front of the sofa (the model is a table: set at 0.45 m)
TZ = 0.45
table = load('ph-WoodenTable_01', 'WoodenTable_01_2k.gltf', height=TZ, loc=(0.25, -1.1, 0))
book = load('ph-binder_notebook', 'binder_notebook_2k.gltf', height=0.03, loc=(-0.05, -1.05, TZ), yaw=math.radians(-14))
plant = load('ph-potted_plant_04', 'potted_plant_04_2k.gltf', height=0.22, loc=(0.62, -0.95, TZ), yaw=math.radians(20))
lamp = load('ph-industrial_pipe_lamp', 'industrial_pipe_lamp_2k.gltf', height=0.45, loc=(1.55, -0.2, 0.0), yaw=math.radians(35))
# the phone lies on the table, screen up (the kit's phone stands with its screen toward -Y: laid back flat)
phone = kit.phone(kit.args('phone'), frames=N, width=0.0715, height=0.1470, depth=0.0082, corner=0.011, location=(0, 0, 0))
phone.rotation_euler = (math.radians(-90), 0, math.radians(-18)); phone.location = (0.04, -1.40, TZ + 0.006)
# the TV: a slim box on a low unit, 1.3 m wide, its screen an image sequence (emission)
bpy.ops.mesh.primitive_cube_add(size=1); tvb = bpy.context.active_object; tvb.name = 'tv-body'; tvb.scale = (1.12, 0.035, 0.64); tvb.location = (0.0, 2.5, 0.88)
tvb.data.materials.append(kit.material('tv-body', '#0b0b10', metallic=0.6, rough=0.3))
bpy.ops.mesh.primitive_cube_add(size=1); unit = bpy.context.active_object; unit.name = 'tv-unit'; unit.scale = (1.5, 0.38, 0.38); unit.location = (0.0, 2.5, 0.19)
unit.data.materials.append(kit.material('unit', '#26262c', metallic=0.1, rough=0.45))
bpy.ops.mesh.primitive_cube_add(size=1); top = bpy.context.active_object; top.name = 'tv-unit-top'; top.scale = (1.52, 0.40, 0.03); top.location = (0.0, 2.5, 0.395)
top.data.materials.append(kit.material('unit-top', '#4a3a2e', metallic=0.0, rough=0.35))
bpy.ops.mesh.primitive_plane_add(size=1); scr = bpy.context.active_object; scr.name = 'tv-screen'; scr.rotation_euler = (math.radians(90), 0, 0); scr.scale = (1.09, 0.615, 1.0); scr.location = (0.0, 2.481, 0.88)
sm = bpy.data.materials.new('tv-screen'); sm.use_nodes = True; nt = sm.node_tree; nt.nodes.clear()
tex = nt.nodes.new('ShaderNodeTexImage'); first = kit.args('tv') % 1 if '%' in kit.args('tv', '') else kit.args('tv')
img = bpy.data.images.load(first); img.source = 'SEQUENCE'; tex.image = img; tex.image_user.frame_duration = N; tex.image_user.frame_start = 0; tex.image_user.use_auto_refresh = True
em = nt.nodes.new('ShaderNodeEmission'); em.inputs['Strength'].default_value = kit.args('tvlight', 6.0, float); out = nt.nodes.new('ShaderNodeOutputMaterial')
nt.links.new(tex.outputs['Color'], em.inputs['Color']); nt.links.new(em.outputs[0], out.inputs['Surface']); scr.data.materials.append(sm)
# a floor that only catches shadows: the objects stand on the HDRI's floor (contact shadows, no floating)
bpy.ops.mesh.primitive_plane_add(size=40, location=(0, 0, 0)); fl = bpy.context.active_object; fl.name = 'floor'; fl.is_shadow_catcher = True
# soft contact shadows under what stands on the floor (the HDRI's light is diffuse: without them an object looks stuck on the picture)
def blob(x, y, w, d, a=0.75):
    bpy.ops.mesh.primitive_plane_add(size=1, location=(x, y, 0.003)); b = bpy.context.active_object; b.name = 'contact'; b.scale = (w, d, 1); b.visible_shadow = False
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
blob(0.0, 2.5, 2.1, 0.9); blob(1.55, -0.2, 0.7, 0.7, 0.6); blob(0.25, -1.1, 2.2, 1.3, 0.5)
# the practical lights: the lamp (warm) and the spill of the TV on the table
bpy.ops.object.light_add(type='POINT', location=(1.5, -0.15, 0.55)); l = bpy.context.active_object; l.data.energy = 90; l.data.color = (1.0, 0.76, 0.5); l.data.shadow_soft_size = 0.08
bpy.ops.object.light_add(type='AREA', location=(0.0, 1.2, 0.95)); tvl = bpy.context.active_object; tvl.data.energy = 60; tvl.data.size = 1.2; tvl.data.color = (0.55, 0.7, 1.0)
tvl.rotation_euler = (math.radians(90 + 25), 0, 0)
# Roger's knees at the bottom of the frame: he is there
for sx in (-0.14, 0.14):
    bpy.ops.mesh.primitive_cylinder_add(radius=0.095, depth=0.5, location=(sx, -1.95, 0.62), rotation=(math.radians(90), 0, 0)); th = bpy.context.active_object; th.name = 'knee'
    th.data.materials.append(kit.material('jeans', '#23304a', metallic=0.0, rough=0.85))
# the eyes: seated on the sofa, 1.05 m high, 2.3 m from the TV; breathing; turns to the TV, looks down at the phone, comes back
bpy.ops.object.camera_add(); cam = bpy.context.active_object; s.camera = cam
cam.data.lens = 24; cam.data.dof.use_dof = True; cam.data.dof.aperture_fstop = 2.0
focus = bpy.data.objects.new('focus', None); bpy.context.collection.objects.link(focus); cam.data.dof.focus_object = focus
tc = cam.constraints.new('TRACK_TO'); tc.target = focus; tc.track_axis = 'TRACK_NEGATIVE_Z'; tc.up_axis = 'UP_Y'
LOOK = {'tv': (0.0, 2.5, 0.88), 'phone': (0.04, -1.40, TZ), 'room': (-0.6, 2.5, 1.45)}
CUES = kit.args('cues', '0:room,5:tv,10:phone,16:tv').split(',')
keys = [(float(c.split(':')[0]) * FPS, LOOK[c.split(':')[1]]) for c in CUES]
def look(i):
    for (a, pa), (b, pb) in zip(keys, keys[1:]):
        if i <= b:
            u = (i - a) / max(1, b - a); u = u * u * (3 - 2 * u); return [pa[k] + (pb[k] - pa[k]) * u for k in range(3)]
    return list(keys[-1][1])
# the eyes lean in to the film that plays on the TV: lens keys « seconds:lens » (smooth between two keys, nothing outside the first and the last)
ZK = [(float(k.split(':')[0]), float(k.split(':')[1])) for k in kit.args('zoom', '').split(',') if k]
def zoom(t):
    if not ZK or t < ZK[0][0] or t > ZK[-1][0]: return None
    for (a, la), (b, lb) in zip(ZK, ZK[1:]):
        if t <= b:
            u = (t - a) / max(1e-6, b - a); u = u * u * (3 - 2 * u); return la + (lb - la) * u
    return ZK[-1][1]
for i in range(N):
    breathe = 0.006 * math.sin(i / FPS * 1.3)
    cam.location = (0.0 + 0.004 * math.sin(i / FPS * 0.8), -2.2, 1.05 + breathe); cam.keyframe_insert('location', frame=i)
    focus.location = look(i); focus.keyframe_insert('location', frame=i)
    d = math.dist(focus.location, (0, -2.2, 1.05)); near = max(0.0, min(1.0, (3.0 - d) / 1.2))       # 0 far (TV, room) .. 1 the phone
    lens = 24 + 44 * near * near * (3 - 2 * near)
    z = zoom(i / FPS)
    if z is not None: lens = z
    cam.data.lens = lens; cam.data.keyframe_insert('lens', frame=i)
OUT = kit.args('out', '/tmp/rfp/s'); still = kit.args('still', None, int)
skip = kit.args('skip', ''); only = [tuple(int(x) for x in r.split(':')) for r in kit.args('only', '').split(',') if r]       # a:b — the frames NOT rendered (the film that plays on the TV is laid over one plate instead: see make.sh)
a, b = (int(x) for x in skip.split(':')) if skip else (0, 0)
os.makedirs(OUT, exist_ok=True); s.frame_start, s.frame_end = 0, N - 1
if still is not None:
    s.frame_set(still); s.render.filepath = os.path.join(OUT, '%05d.png' % still); bpy.ops.render.render(write_still=True)
    # where the four corners of the TV screen fall in the picture (0..1, origin top left): the film is warped onto them
    from bpy_extras.object_utils import world_to_camera_view
    pts = [scr.matrix_world @ mathutils.Vector(v) for v in ((-.5, -.5, 0), (.5, -.5, 0), (.5, .5, 0), (-.5, .5, 0))]
    c = [world_to_camera_view(s, cam, p) for p in pts]
    import json; json.dump([[round(q.x, 5), round(1 - q.y, 5)] for q in c], open(os.path.join(OUT, 'tv-corners.json'), 'w'))
else:
    for f in range(N):
        if a <= f < b: continue
        if only and not any(x <= f < y for x, y in only): continue
        path = os.path.join(OUT, '%05d.png' % f)
        if os.path.exists(path) and os.path.getsize(path) > 0 and not os.path.exists(path + '.part'): continue
        open(path + '.part', 'w').close(); s.frame_set(f); s.render.filepath = path; bpy.ops.render.render(write_still=True); os.remove(path + '.part')
