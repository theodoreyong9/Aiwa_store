"""v28, ONE scene: the real desk, its objects and the constellations are the same 3D world, filmed by one camera.
The constellations engine (docs/videos/aiwa-v28/video-film.html) is dumped by video-kit/src/dump-constellations.mjs (positions of every point per frame, camera moves relative to each sculpture);
here every point is a small emissive sphere, every thread a thin glowing tube, the camera is the engine's own move (yaw, pitch, distance, roll) around each sculpture's anchor in the
room, and the objects of the room take part: the phone and the spectacles rise into the sculpture of their page, the notebook floats up under the types, the lamp pulses with the
super agent, the plant sways, and the points light the desk.
  /tmp/bpyenv/bin/python scene.py -- --data /tmp/v28/data --ph /tmp/ph --screen /tmp/v28/scr/%04d.png --out /tmp/v28/s [--from 0 --to 609 | --still 300] [--width 960 --height 540 --samples 10]"""
import os, sys, math, json
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import numpy as np
import kit

DATA = kit.args('data', '/tmp/v28/data'); ph = kit.args('ph', '/tmp/ph')
meta = json.load(open(DATA + '/meta.json')); fr = json.load(open(DATA + '/frames.json'))
FPS = fr['fps']; NFR = fr['frames']; N = meta['N']
kit.reset(transparent=False)
s = bpy.context.scene
s.render.fps = FPS
kit.hdri(os.path.join(ph, 'ph-lythwood_lounge', 'lythwood_lounge_2k.hdr'), strength=0.20, rotation=math.radians(200), visible=True)
_nt = s.world.node_tree; _bg = _nt.nodes['Background']; _env = [n for n in _nt.nodes if n.type == 'TEX_ENVIRONMENT'][0]
_dim = _nt.nodes.new('ShaderNodeBackground'); _dim.inputs['Strength'].default_value = 0.007; _nt.links.new(_env.outputs['Color'], _dim.inputs['Color'])
_ray = _nt.nodes.new('ShaderNodeLightPath'); _mix = _nt.nodes.new('ShaderNodeMixShader')
_nt.links.new(_ray.outputs['Is Camera Ray'], _mix.inputs['Fac']); _nt.links.new(_bg.outputs['Background'], _mix.inputs[1]); _nt.links.new(_dim.outputs['Background'], _mix.inputs[2])
_nt.links.new(_mix.outputs['Shader'], _nt.nodes['World Output'].inputs['Surface'])
s.cycles.max_bounces = 4; s.cycles.diffuse_bounces = 2; s.cycles.glossy_bounces = 2; s.cycles.transmission_bounces = 2; s.cycles.volume_bounces = 0
s.view_settings.look = 'None'

def load(asset, gltf, height=None, loc=(0, 0, 0), yaw=0.0):
    return kit.import_asset(os.path.join(ph, asset, gltf), height=height, location=loc, yaw=yaw)
Z = 0.74
table = load('ph-WoodenTable_01', 'WoodenTable_01_2k.gltf', height=Z, loc=(0, 0, 0))
book = load('ph-binder_notebook', 'binder_notebook_2k.gltf', height=0.035, loc=(0.02, -0.02, Z), yaw=math.radians(-12))
spec = load('ph-round_spectacles', 'round_spectacles_2k.gltf', height=0.045, loc=(0.26, -0.03, Z + 0.036), yaw=math.radians(-28))
plant = load('ph-potted_plant_04', 'potted_plant_04_2k.gltf', height=0.16, loc=(0.52, 0.20, Z), yaw=math.radians(20))
lamp = load('ph-industrial_pipe_lamp', 'industrial_pipe_lamp_2k.gltf', height=0.42, loc=(-0.45, 0.22, Z), yaw=math.radians(35))
phone = kit.phone(kit.args('screen'), frames=NFR, width=0.0715, height=0.1470, depth=0.0082, corner=0.011, location=(0, 0, 0))
REST = {  # (location, rotation euler, scale) of the props at rest
    'book': (book.location.copy(), book.rotation_euler.copy(), book.scale.copy()),
    'spec': (spec.location.copy(), spec.rotation_euler.copy(), spec.scale.copy()),
    'plant': (plant.location.copy(), plant.rotation_euler.copy(), plant.scale.copy()),
    'phone': (mathutils.Vector((-0.15, -0.045, Z + 0.108)), mathutils.Euler((math.radians(-22), 0, math.radians(14))), mathutils.Vector((1, 1, 1))),
}
bpy.ops.object.light_add(type='POINT', location=(-0.45, 0.14, Z + 0.36)); lamp_l = bpy.context.active_object; lamp_l.data.color = (1.0, 0.78, 0.55); lamp_l.data.shadow_soft_size = 0.05
bpy.ops.object.light_add(type='AREA', location=(0.5, -0.5, Z + 0.5)); rim = bpy.context.active_object; rim.data.energy = 14; rim.data.size = 0.6; rim.data.color = (1.0, 0.45, 0.28)
rim.rotation_euler = mathutils.Vector((-0.5, 0.5, -0.7)).to_track_quat('-Z', 'Y').to_euler()
bpy.ops.object.light_add(type='POINT', location=(0, 0, Z + 0.3)); spill = bpy.context.active_object; spill.data.color = (0.6, 0.74, 1.0); spill.data.shadow_soft_size = 0.12

# the sculptures live above the desk, one anchor per slot, each turned a little differently so that the room behind changes with the page
ANCH = {0: (0.0, -0.02, Z + 0.27), 1: (0.0, 0.0, Z + 0.27), 2: (0.05, -0.04, Z + 0.28), 3: (0.18, 0.0, Z + 0.28), 4: (0.0, 0.05, Z + 0.30), 5: (-0.28, 0.06, Z + 0.32), 6: (0.02, 0.08, Z + 0.34), 7: (0.0, -0.02, Z + 0.27)}
THETA = {0: 0.0, 1: 0.30, 2: -0.25, 3: 0.20, 4: -0.40, 5: 0.28, 6: -0.15, 7: 0.0}
SC = 0.00045   # metres per unit of the engine's world

def rz(v, a):
    c, sn = math.cos(a), math.sin(a)
    return np.array([v[0] * c - v[1] * sn, v[0] * sn + v[1] * c, v[2]])

def to_room(slot, v):   # a vector in the sculpture's frame (x right, y up, z toward the camera) -> the room (x right, y away, z up)
    return rz(np.array([v[0], -v[2], v[1]]) * SC, THETA[slot])

cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); bpy.context.collection.objects.link(cam); s.camera = cam
cam.data.sensor_width = 36; cam.data.lens = 18 / math.tan(math.atan(960 / 1400)); cam.data.shift_y = -(540 - 420) / 1920
cam.data.dof.use_dof = True; cam.data.dof.aperture_fstop = 1.6
cam.data.clip_start = 0.02

# ---- the points: icospheres, one per point; the threads: edges with a wireframe, one mesh per set of threads
t_ = (1 + 5 ** 0.5) / 2
IV = np.array([(-1, t_, 0), (1, t_, 0), (-1, -t_, 0), (1, -t_, 0), (0, -1, t_), (0, 1, t_), (0, -1, -t_), (0, 1, -t_), (t_, 0, -1), (t_, 0, 1), (-t_, 0, -1), (-t_, 0, 1)], float)
IV /= np.linalg.norm(IV[0])
IF = np.array([(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6), (7, 1, 8), (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1)], int)

def emission_material(name, color_attr=None, color=(1, 1, 1), strength=10.0):
    m = bpy.data.materials.new(name); m.use_nodes = True; nt = m.node_tree; nt.nodes.clear()
    em = nt.nodes.new('ShaderNodeEmission'); out = nt.nodes.new('ShaderNodeOutputMaterial')
    if color_attr:
        at = nt.nodes.new('ShaderNodeAttribute'); at.attribute_name = color_attr; nt.links.new(at.outputs['Color'], em.inputs['Color'])
    else:
        em.inputs['Color'].default_value = (*color, 1)
    val = nt.nodes.new('ShaderNodeValue'); val.outputs[0].default_value = strength
    nt.links.new(val.outputs[0], em.inputs['Strength']); nt.links.new(em.outputs[0], out.inputs['Surface'])
    return m, val

BONE = (0.88, 0.85, 0.78); VERM = (1.0, 0.16, 0.035)
units = {}
for u in meta['units']:
    k = u['k']
    pts = bpy.data.meshes.new(f'pts{k}')
    pts.vertices.add(N * 12); pts.loops.add(N * 60); pts.polygons.add(N * 20)
    faces = (IF[None, :, :] + 12 * np.arange(N)[:, None, None]).reshape(-1, 3)
    pts.polygons.foreach_set('loop_start', np.arange(0, N * 60, 3, dtype=np.int32)); pts.polygons.foreach_set('loop_total', np.full(N * 20, 3, np.int32))
    pts.loops.foreach_set('vertex_index', faces.reshape(-1).astype(np.int32))
    pts.update(calc_edges=True)
    verm = np.array(u['verm'], bool)
    col = np.where(verm[:, None], np.array(VERM), np.array(BONE))
    pts.color_attributes.new('Col', 'FLOAT_COLOR', 'POINT')
    pts.color_attributes['Col'].data.foreach_set('color', np.concatenate([np.repeat(col, 12, axis=0), np.ones((N * 12, 1))], axis=1).reshape(-1).astype(np.float32))
    po = bpy.data.objects.new(f'pts{k}', pts); bpy.context.collection.objects.link(po)
    pm, pv = emission_material(f'pm{k}', 'Col', strength=18.0); po.data.materials.append(pm)
    po.visible_shadow = False
    sets = []
    for si, ed in enumerate(u['sets']):
        e = np.array(ed, np.int32).reshape(-1, 2); E = len(e)
        me = bpy.data.meshes.new(f'thr{k}_{si}'); me.vertices.add(E * 6); me.loops.add(E * 12); me.polygons.add(E * 3)
        ring = np.array([[0, 1, 4, 3], [1, 2, 5, 4], [2, 0, 3, 5]])      # three quads round a prism: vertices 0-2 at one end, 3-5 at the other
        quads = (ring[None] + 6 * np.arange(E)[:, None, None]).reshape(-1, 4)
        me.polygons.foreach_set('loop_start', np.arange(0, E * 12, 4, dtype=np.int32)); me.polygons.foreach_set('loop_total', np.full(E * 3, 4, np.int32))
        me.loops.foreach_set('vertex_index', quads.reshape(-1).astype(np.int32)); me.update(calc_edges=True)
        eo = bpy.data.objects.new(f'thr{k}_{si}', me); bpy.context.collection.objects.link(eo)
        tm, tv = emission_material(f'tm{k}_{si}', color=(0.95, 0.92, 0.86), strength=2.2); eo.data.materials.append(tm); eo.visible_shadow = False
        sets.append((eo, tv, e))
    base = np.where(np.arange(N) % 6 == 0, np.where(verm, 6.5, 5.0), np.where(verm, 3.4, 2.1) + np.array(u['sz']) * 1.2)
    nextStart = meta['scn'][u['last'] + 1][0] if u['last'] + 1 < meta['NS'] else 1e9
    pos = np.fromfile(f"{DATA}/pos_{k}.f32", np.float32).reshape(-1, N, 3)
    vis = fr['vis'][str(k)]
    units[k] = dict(u=u, po=po, pm=pv, sets=sets, base=base, nextStart=nextStart, pos=pos, vis={v['f']: (i, v) for i, v in enumerate(vis)})

def smooth(x): x = max(0.0, min(1.0, x)); return x * x * (3 - 2 * x)

def set_frame(f):
    t = f / FPS
    c = fr['cam'][f]
    # ---- the camera: the engine's move around the anchor of the slot it is in (and the next one while it flies)
    a, b = c['slotA'], c['slotB']
    posA = np.array(ANCH[a]) + to_room(a, c['relA']); tgA = np.array(ANCH[a])
    if a != b and c['u'] > 0:
        e = smooth(c['u']); posB = np.array(ANCH[b]) + to_room(b, c['relB']); tgB = np.array(ANCH[b])
        pos = posA * (1 - e) + posB * e + np.array([0, 0, 0.07 * math.sin(math.pi * e)]); tg = tgA * (1 - e) + tgB * e
    else:
        pos, tg = posA, tgA
    pos = pos + np.array([0.003 * math.sin(t * 1.1), 0, 0.002 * math.cos(t * 0.9)])
    cam.location = pos
    d = mathutils.Vector(tg - pos); q = d.to_track_quat('-Z', 'Y'); eul = q.to_euler()
    m = q.to_matrix().to_4x4() @ mathutils.Matrix.Rotation(c['roll'], 4, 'Z')
    cam.rotation_euler = m.to_euler(); cam.data.dof.focus_distance = float(d.length)
    # ---- the sculptures
    glow_pos = np.zeros(3); glow_w = 0.0
    for k, U in units.items():
        po = U['po']; slot = U['u']['slot']
        v = U['vis'].get(f)
        on = v is not None
        po.hide_render = not on
        for eo, tv, ed in U['sets']: eo.hide_render = True
        if not on: continue
        i, vv = v
        ao = 1.0 - max(0.0, min(1.0, (t - U['nextStart']) / 0.5)); al = vv['al'] * ao
        if al <= 0.003: po.hide_render = True; continue
        P = U['pos'][i].astype(np.float64)
        cs, sn = math.cos(vv['psi']), math.sin(vv['psi'])
        X = P[:, 0] * cs + P[:, 2] * sn; Zl = -P[:, 0] * sn + P[:, 2] * cs; Y = P[:, 1]
        loc = np.stack([X, -Zl, Y], 1) * SC
        loc = np.stack([loc[:, 0] * math.cos(THETA[slot]) - loc[:, 1] * math.sin(THETA[slot]), loc[:, 0] * math.sin(THETA[slot]) + loc[:, 1] * math.cos(THETA[slot]), loc[:, 2]], 1) + np.array(ANCH[slot])
        r = (U['base'] * 0.46 * SC * min(1.0, al * 1.4))[:, None, None]
        verts = (loc[:, None, :] + r * IV[None]).reshape(-1, 3).astype(np.float32)
        po.data.vertices.foreach_set('co', verts.reshape(-1)); po.data.update()
        U['pm'].outputs[0].default_value = 18.0 * min(1.0, al)
        for si, (eo, tv, ed) in enumerate(U['sets']):
            w = (1 - vv['mB'] if vv['pb'] >= 0 else 1.0) if si == vv['pa'] else (vv['mB'] if si == vv['pb'] else 0.0)
            if w * al < 0.02: continue
            eo.hide_render = False
            A_, B_ = loc[ed[:, 0]], loc[ed[:, 1]]
            dvec = B_ - A_; dl = np.linalg.norm(dvec, axis=1, keepdims=True) + 1e-9; dn = dvec / dl
            ref = np.where(np.abs(dn[:, 2:3]) < 0.9, np.array([[0, 0, 1.0]]), np.array([[1.0, 0, 0]]))
            n1 = np.cross(dn, ref); n1 /= np.linalg.norm(n1, axis=1, keepdims=True) + 1e-9; n2 = np.cross(dn, n1)
            rad = 0.00030
            offs = [n1 * rad, (-0.5 * n1 + 0.866 * n2) * rad, (-0.5 * n1 - 0.866 * n2) * rad]
            V = np.stack([A_ + offs[0], A_ + offs[1], A_ + offs[2], B_ + offs[0], B_ + offs[1], B_ + offs[2]], 1).reshape(-1, 3)
            eo.data.vertices.foreach_set('co', V.astype(np.float32).reshape(-1)); eo.data.update()
            tv.outputs[0].default_value = 2.2 * w * al
        glow_pos += np.array(ANCH[slot]) * al; glow_w += al
    spill.location = (glow_pos / glow_w) if glow_w > 0 else (0, 0, Z + 0.3)
    spill.data.energy = 22.0 * min(1.5, glow_w)
    # ---- the objects of the room take part
    lift = lambda t0, t1, t2, t3: smooth((t - t0) / (t1 - t0)) * (1 - smooth((t - t2) / (t3 - t2)))
    # the phone rises into the sculpture of the widget page, and goes back when the spectacles take its place
    e = lift(13.2, 14.2, 18.9, 19.9); A2 = np.array(ANCH[2])
    loc0, rot0, sc0 = REST['phone']
    ph_loc = np.array(loc0) * (1 - e) + (A2 + np.array([-0.0, 0, 0.0])) * e
    phone.location = ph_loc + np.array([0, 0, 0.012 * math.sin(t * 2.0) * e])
    phone.rotation_euler = (rot0[0] * (1 - e), 0, rot0[2] * (1 - e) + (THETA[2] * 0.0 + 0.25 * math.sin(t * 0.8)) * e)
    e2 = lift(18.9, 19.9, 22.0, 23.0)
    loc0, rot0, sc0 = REST['spec']
    spec.location = np.array(loc0) * (1 - e2) + A2 * e2 + np.array([0, 0, 0.01 * math.sin(t * 2.3) * e2])
    spec.rotation_euler = (rot0[0], rot0[1], rot0[2] + (t - 19.0) * 0.9 * e2)
    spec.scale = tuple(np.array(sc0) * (1 + 0.9 * e2))
    # the notebook floats under the types (slot 1) and turns with them
    e3 = lift(2.3, 3.4, 12.0, 13.2); loc0, rot0, sc0 = REST['book']
    book.location = (loc0[0], loc0[1], loc0[2] + 0.035 * e3 + 0.004 * math.sin(t * 1.7) * e3)
    book.rotation_euler = (rot0[0], rot0[1], rot0[2] + 0.35 * math.sin((t - 2.3) * 0.5) * e3)
    # the plant always breathes, and swells with the world; the lamp pulses with the super agent
    loc0, rot0, sc0 = REST['plant']
    plant.rotation_euler = (rot0[0] + 0.02 * math.sin(t * 1.3), rot0[1] + 0.015 * math.sin(t * 1.1 + 1), rot0[2])
    sa = lift(31.0, 31.8, 35.8, 36.6)
    lamp_l.data.energy = 70 * (1 + 1.2 * sa * (math.sin(t * 6.5) ** 2) + 0.6 * lift(44.5, 45.2, 49.5, 50.6))

frames = range(NFR)
still = kit.args('still', None, int)
if still is not None: frames = [still]
else: frames = range(kit.args('from', 0, int), kit.args('to', NFR, int))
out = kit.args('out', '/tmp/v28/s'); os.makedirs(out, exist_ok=True)
for f in frames:
    path = os.path.join(out, '%05d.png' % f)
    if still is None and os.path.exists(path) and os.path.getsize(path) > 0 and not os.path.exists(path + '.part'): continue
    open(path + '.part', 'w').close()
    set_frame(f); s.frame_set(f); s.render.filepath = path; bpy.ops.render.render(write_still=True)
    os.remove(path + '.part')
