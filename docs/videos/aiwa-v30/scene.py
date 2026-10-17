"""v30, ONE world that changes places: the objects of the room take part in the animation (they disintegrate into the points of the sculpture of their page and
re-integrate afterwards), the flat details of each page hang in the room at the sculpture, the camera follows the engine's own moves and flies from one place to the next,
and the place itself changes with the theme of the page (a loft studio, a night city, a neon studio, a foundry, a night sky), with a film playing on a wall in two of them.

  /tmp/bpyenv/bin/python scene.py -- --data /tmp/v30/data --ph /tmp/ph --out /tmp/v30/s [--ovl /tmp/v30/ovl --scr /tmp/v30/scrraw --walls /tmp/v25/frames,/tmp/v24/frames]
         [--from 0 --to 609 | --still 300] [--width 960 --height 540 --samples 8]

The data: video-kit/src/dump-constellations.mjs. The output folder carries a stamp of everything that makes the images (this script, the data, the size, the samples,
the HDRIs): a relaunch with another stamp starts again from nothing, with the same stamp it goes on where it stopped (so that a film never mixes two looks).
"""
import os, sys, math, json, hashlib, glob
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import numpy as np
import kit

DATA = kit.args('data', '/tmp/v30/data'); PH = kit.args('ph', '/tmp/ph'); OUT = kit.args('out', '/tmp/v30/s')
OVL = kit.args('ovl', '/tmp/v30/ovl'); SCR = kit.args('scr', '/tmp/v30/scrraw'); WALLS = kit.args('walls', '/tmp/v25/frames,/tmp/v24/frames').split(',')
W_, H_, SAMPLES = kit.args('width', 960, int), kit.args('height', 540, int), kit.args('samples', 8, int)
meta = json.load(open(DATA + '/meta.json')); fr = json.load(open(DATA + '/frames.json'))
FPS = fr['fps']; NFR = fr['frames']; N = meta['N']; SLOT = meta['SLOT']; SCN = meta['scn']
still = kit.args('still', None, int)

# ---- the stamp: what makes the images; another stamp, another film
ENVS = [  # (folder, file, light strength, strength seen by the camera, rotation in degrees)
    ('ph-lythwood_lounge', 'lythwood_lounge_2k.hdr', 0.20, 0.007, 200),
    ('ph-photo_studio_loft_hall', 'photo_studio_loft_hall_2k.hdr', 0.30, 0.03, 40),
    ('ph-shanghai_bund', 'shanghai_bund_2k.hdr', 1.1, 0.45, 120),
    ('ph-neon_photostudio', 'neon_photostudio_2k.hdr', 0.55, 0.22, 10),
    ('ph-industrial_workshop_foundry', 'industrial_workshop_foundry_2k.hdr', 0.5, 0.10, 80),
    ('ph-rogland_clear_night', 'rogland_clear_night_2k.hdr', 1.6, 0.9, 0),
]
SLOTENV = {0: 0, 1: 1, 2: 2, 3: 3, 4: 4, 5: 4, 6: 5, 7: 0}
h = hashlib.sha1()
for p in (os.path.abspath(__file__), DATA + '/frames.json', DATA + '/meta.json'):
    h.update(open(p, 'rb').read())
h.update(json.dumps([W_, H_, SAMPLES, ENVS, sorted(os.path.basename(p) for p in glob.glob(DATA + '/pos_*.f32')), len(glob.glob(OVL + '/*.png'))]).encode())
STAMP = h.hexdigest()[:16]
os.makedirs(OUT, exist_ok=True)
stamp_file = os.path.join(OUT, '.stamp')
if still is None:
    old = open(stamp_file).read().strip() if os.path.exists(stamp_file) else None
    if old != STAMP:
        for p in glob.glob(OUT + '/*.png') + glob.glob(OUT + '/*.part'): os.remove(p)
        open(stamp_file, 'w').write(STAMP)
        print('stamp', STAMP, '(new: the folder was emptied)' if old else '(first start)')
    else:
        print('stamp', STAMP, '(same: going on)')

kit.reset(width=W_, height=H_, samples=SAMPLES, transparent=False)
s = bpy.context.scene
s.render.fps = FPS
s.cycles.max_bounces = 4; s.cycles.diffuse_bounces = 2; s.cycles.glossy_bounces = 2; s.cycles.transmission_bounces = 2; s.cycles.volume_bounces = 0
s.cycles.transparent_max_bounces = 12

# ---- the world: two HDRIs mixed (the place we leave and the place we reach), one strength for the light and one for what the camera sees
nt = s.world.node_tree; nt.nodes.clear()
tcn = nt.nodes.new('ShaderNodeTexCoord')
def env_node():
    mp = nt.nodes.new('ShaderNodeMapping'); en = nt.nodes.new('ShaderNodeTexEnvironment')
    nt.links.new(tcn.outputs['Generated'], mp.inputs['Vector']); nt.links.new(mp.outputs['Vector'], en.inputs['Vector'])
    return mp, en
mapA, envA = env_node(); mapB, envB = env_node()
mixc = nt.nodes.new('ShaderNodeMix'); mixc.data_type = 'RGBA'
nt.links.new(envA.outputs['Color'], mixc.inputs[6]); nt.links.new(envB.outputs['Color'], mixc.inputs[7])
bgL = nt.nodes.new('ShaderNodeBackground'); bgC = nt.nodes.new('ShaderNodeBackground')
nt.links.new(mixc.outputs[2], bgL.inputs['Color']); nt.links.new(mixc.outputs[2], bgC.inputs['Color'])
ray = nt.nodes.new('ShaderNodeLightPath'); mixs = nt.nodes.new('ShaderNodeMixShader'); wout = nt.nodes.new('ShaderNodeOutputWorld')
nt.links.new(ray.outputs['Is Camera Ray'], mixs.inputs['Fac']); nt.links.new(bgL.outputs['Background'], mixs.inputs[1]); nt.links.new(bgC.outputs['Background'], mixs.inputs[2])
nt.links.new(mixs.outputs['Shader'], wout.inputs['Surface'])
IMGS = [bpy.data.images.load(os.path.join(PH, e[0], e[1])) for e in ENVS]

def smooth(x): x = max(0.0, min(1.0, x)); return x * x * (3 - 2 * x)
def smoothv(x): x = np.clip(x, 0.0, 1.0); return x * x * (3 - 2 * x)

# ---- the room: the table, and the five objects that take part
def load(asset, gltf, height=None, loc=(0, 0, 0), yaw=0.0):
    return kit.import_asset(os.path.join(PH, asset, gltf), height=height, location=loc, yaw=yaw)
Z = 0.74
table = load('ph-WoodenTable_01', 'WoodenTable_01_2k.gltf', height=Z, loc=(0, 0, 0))
book = load('ph-binder_notebook', 'binder_notebook_2k.gltf', height=0.035, loc=(0.02, -0.02, Z), yaw=math.radians(-12))
spec = load('ph-round_spectacles', 'round_spectacles_2k.gltf', height=0.045, loc=(0.26, -0.03, Z + 0.036), yaw=math.radians(-28))
plant = load('ph-potted_plant_04', 'potted_plant_04_2k.gltf', height=0.16, loc=(0.52, 0.20, Z), yaw=math.radians(20))
lamp = load('ph-industrial_pipe_lamp', 'industrial_pipe_lamp_2k.gltf', height=0.42, loc=(-0.45, 0.22, Z), yaw=math.radians(35))
phone = kit.phone(os.path.join(SCR, '00000.jpg'), frames=1, width=0.0715, height=0.1470, depth=0.0082, corner=0.011, location=(0, 0, 0))
PROPS = {'book': book, 'spec': spec, 'plant': plant, 'lamp': lamp, 'phone': phone}
REST = {k: (o.location.copy(), o.rotation_euler.copy(), o.scale.copy()) for k, o in PROPS.items()}
REST['phone'] = (mathutils.Vector((-0.15, -0.045, Z + 0.108)), mathutils.Euler((math.radians(-22), 0, math.radians(14))), mathutils.Vector((1, 1, 1)))
phone.location = REST['phone'][0]; phone.rotation_euler = REST['phone'][1]
bpy.context.view_layer.update()

# the phone's screen: ONE image that is read again at each frame (no sequence to get out of step)
scr_img = None
for ms in phone.material_slots:
    if ms.material and ms.material.use_nodes:
        for n in ms.material.node_tree.nodes:
            if n.type == 'TEX_IMAGE':
                scr_img = bpy.data.images.load(os.path.join(SCR, '00000.jpg')); scr_img.source = 'FILE'; n.image = scr_img

# ---- the points of an object: its surface, sampled once in the object's own space, so that they follow it when it moves
rng = np.random.default_rng(7)
def surface_samples(root, n):
    tris, areas = [], []
    for o in [root, *root.children_recursive]:
        if o.type != 'MESH': continue
        me = o.data; me.calc_loop_triangles()
        co = np.empty(len(me.vertices) * 3, np.float32); me.vertices.foreach_get('co', co); co = co.reshape(-1, 3)
        idx = np.empty(len(me.loop_triangles) * 3, np.int32); me.loop_triangles.foreach_get('vertices', idx); idx = idx.reshape(-1, 3)
        M = np.array(o.matrix_world); w = co @ M[:3, :3].T + M[:3, 3]
        t = w[idx]; a = 0.5 * np.linalg.norm(np.cross(t[:, 1] - t[:, 0], t[:, 2] - t[:, 0]), axis=1)
        tris.append(t); areas.append(a)
    tris = np.concatenate(tris); areas = np.concatenate(areas); p = areas / areas.sum()
    pick = rng.choice(len(tris), n, p=p); u = rng.random((n, 2)); flip = u.sum(1) > 1; u[flip] = 1 - u[flip]
    pts = tris[pick, 0] + u[:, :1] * (tris[pick, 1] - tris[pick, 0]) + u[:, 1:] * (tris[pick, 2] - tris[pick, 0])
    Minv = np.array(root.matrix_world.inverted())
    return (pts @ Minv[:3, :3].T + Minv[:3, 3]).astype(np.float64)       # in the object's space
SAMPLES_LOCAL = {k: surface_samples(o, N) for k, o in PROPS.items()}

# the objects dissolve (a noise cut, the way a thing turns to dust) as their points leave; one value per object
DISSOLVE = {}
def dissolvable(key, root):
    vals = []
    mats = set()
    for o in [root, *root.children_recursive]:
        for slot in o.material_slots:
            if slot.material and slot.material.use_nodes: mats.add(slot.material)
    for m in mats:
        t = m.node_tree
        outs = [n for n in t.nodes if n.type == 'OUTPUT_MATERIAL']
        out = next((n for n in outs if n.is_active_output), outs[0])
        if not out.inputs['Surface'].links: continue
        src = out.inputs['Surface'].links[0].from_socket
        tr = t.nodes.new('ShaderNodeBsdfTransparent'); mx = t.nodes.new('ShaderNodeMixShader'); co = t.nodes.new('ShaderNodeTexCoord')
        nz = t.nodes.new('ShaderNodeTexNoise'); nz.inputs['Scale'].default_value = 90.0; nz.inputs['Detail'].default_value = 3.0
        val = t.nodes.new('ShaderNodeValue'); gt = t.nodes.new('ShaderNodeMath'); gt.operation = 'GREATER_THAN'
        t.links.new(co.outputs['Object'], nz.inputs['Vector']); t.links.new(val.outputs[0], gt.inputs[0]); t.links.new(nz.outputs['Fac'], gt.inputs[1])
        t.links.new(gt.outputs[0], mx.inputs['Fac']); t.links.new(src, mx.inputs[1]); t.links.new(tr.outputs[0], mx.inputs[2]); t.links.new(mx.outputs[0], out.inputs['Surface'])
        vals.append(val)
    DISSOLVE[key] = vals
for k, o in PROPS.items(): dissolvable(k, o)

# ---- the lights: the lamp, a rim, and the spill of the constellations on the room
bpy.ops.object.light_add(type='POINT', location=(-0.45, 0.14, Z + 0.36)); lamp_l = bpy.context.active_object; lamp_l.data.color = (1.0, 0.78, 0.55); lamp_l.data.shadow_soft_size = 0.05
bpy.ops.object.light_add(type='AREA', location=(0.5, -0.5, Z + 0.5)); rim = bpy.context.active_object; rim.data.energy = 14; rim.data.size = 0.6; rim.data.color = (1.0, 0.45, 0.28)
rim.rotation_euler = mathutils.Vector((-0.5, 0.5, -0.7)).to_track_quat('-Z', 'Y').to_euler()
bpy.ops.object.light_add(type='POINT', location=(0, 0, Z + 0.3)); spill = bpy.context.active_object; spill.data.color = (0.6, 0.74, 1.0); spill.data.shadow_soft_size = 0.12

# ---- the sculptures' places, the way they are turned, and the objects that feed each (the theme of the page)
ANCH = {0: (0.0, -0.02, Z + 0.27), 1: (0.0, 0.0, Z + 0.27), 2: (0.05, -0.04, Z + 0.28), 3: (0.18, 0.0, Z + 0.28), 4: (0.0, 0.05, Z + 0.30), 5: (-0.28, 0.06, Z + 0.32), 6: (0.02, 0.08, Z + 0.34), 7: (0.0, -0.02, Z + 0.27)}
THETA = {0: 0.0, 1: 0.30, 2: -0.25, 3: 0.20, 4: -0.40, 5: 0.28, 6: -0.15, 7: 0.0}
SOURCES = {0: ['book', 'phone'], 1: ['book', 'plant', 'lamp'], 2: ['lamp', 'plant', 'book'], 3: ['phone', 'spec'], 4: ['lamp', 'plant', 'phone'],
           5: ['lamp'], 6: ['plant', 'book', 'spec', 'lamp', 'phone'], 7: ['book', 'phone', 'spec', 'lamp', 'plant']}
HERO = {2: ('phone', 'spec')}       # in the sculpture of the widget, the phone and the glasses are not dissolved: they rise to the middle of it
SC = 0.00045

def rz(v, a):
    c, sn = math.cos(a), math.sin(a)
    return np.array([v[0] * c - v[1] * sn, v[0] * sn + v[1] * c, v[2]])
def to_room(slot, v):
    return rz(np.array([v[0], -v[2], v[1]]) * SC, THETA[slot])

cam = bpy.data.objects.new('cam', bpy.data.cameras.new('cam')); bpy.context.collection.objects.link(cam); s.camera = cam
cam.data.sensor_width = 36; cam.data.lens = 18 / math.tan(math.atan(960 / 1400)); cam.data.shift_y = -(540 - 420) / 1920
cam.data.dof.use_dof = True; cam.data.dof.aperture_fstop = 1.6; cam.data.clip_start = 0.02

# ---- the points (icospheres) and the threads (prisms)
t_ = (1 + 5 ** 0.5) / 2
IV = np.array([(-1, t_, 0), (1, t_, 0), (-1, -t_, 0), (1, -t_, 0), (0, -1, t_), (0, 1, t_), (0, -1, -t_), (0, 1, -t_), (t_, 0, -1), (t_, 0, 1), (-t_, 0, -1), (-t_, 0, 1)], float)
IV /= np.linalg.norm(IV[0])
IF = np.array([(0, 11, 5), (0, 5, 1), (0, 1, 7), (0, 7, 10), (0, 10, 11), (1, 5, 9), (5, 11, 4), (11, 10, 2), (10, 7, 6), (7, 1, 8), (3, 9, 4), (3, 4, 2), (3, 2, 6), (3, 6, 8), (3, 8, 9), (4, 9, 5), (2, 4, 11), (6, 2, 10), (8, 6, 7), (9, 8, 1)], int)

def emission_material(name, color_attr=None, color=(1, 1, 1), strength=10.0):
    m = bpy.data.materials.new(name); m.use_nodes = True; t = m.node_tree; t.nodes.clear()
    em = t.nodes.new('ShaderNodeEmission'); out = t.nodes.new('ShaderNodeOutputMaterial')
    if color_attr:
        at = t.nodes.new('ShaderNodeAttribute'); at.attribute_name = color_attr; t.links.new(at.outputs['Color'], em.inputs['Color'])
    else:
        em.inputs['Color'].default_value = (*color, 1)
    val = t.nodes.new('ShaderNodeValue'); val.outputs[0].default_value = strength
    t.links.new(val.outputs[0], em.inputs['Strength']); t.links.new(em.outputs[0], out.inputs['Surface'])
    return m, val

BONE = (0.88, 0.85, 0.78); VERM = (1.0, 0.16, 0.035)
units = {}
for u in meta['units']:
    k = u['k']
    pts = bpy.data.meshes.new(f'pts{k}')
    pts.vertices.add(N * 12); pts.loops.add(N * 60); pts.polygons.add(N * 20)
    faces = (IF[None, :, :] + 12 * np.arange(N)[:, None, None]).reshape(-1, 3)
    pts.polygons.foreach_set('loop_start', np.arange(0, N * 60, 3, dtype=np.int32)); pts.polygons.foreach_set('loop_total', np.full(N * 20, 3, np.int32))
    pts.loops.foreach_set('vertex_index', faces.reshape(-1).astype(np.int32)); pts.update(calc_edges=True)
    verm = np.array(u['verm'], bool)
    col = np.where(verm[:, None], np.array(VERM), np.array(BONE))
    pts.color_attributes.new('Col', 'FLOAT_COLOR', 'POINT')
    pts.color_attributes['Col'].data.foreach_set('color', np.concatenate([np.repeat(col, 12, axis=0), np.ones((N * 12, 1))], axis=1).reshape(-1).astype(np.float32))
    po = bpy.data.objects.new(f'pts{k}', pts); bpy.context.collection.objects.link(po)
    pm, pv = emission_material(f'pm{k}', 'Col', strength=18.0); po.data.materials.append(pm); po.visible_shadow = False
    sets = []
    for si, ed in enumerate(u['sets']):
        e = np.array(ed, np.int32).reshape(-1, 2); E = len(e)
        me = bpy.data.meshes.new(f'thr{k}_{si}'); me.vertices.add(E * 6); me.loops.add(E * 12); me.polygons.add(E * 3)
        ring = np.array([[0, 1, 4, 3], [1, 2, 5, 4], [2, 0, 3, 5]])
        quads = (ring[None] + 6 * np.arange(E)[:, None, None]).reshape(-1, 4)
        me.polygons.foreach_set('loop_start', np.arange(0, E * 12, 4, dtype=np.int32)); me.polygons.foreach_set('loop_total', np.full(E * 3, 4, np.int32))
        me.loops.foreach_set('vertex_index', quads.reshape(-1).astype(np.int32)); me.update(calc_edges=True)
        eo = bpy.data.objects.new(f'thr{k}_{si}', me); bpy.context.collection.objects.link(eo)
        tm, tv = emission_material(f'tm{k}_{si}', color=(0.95, 0.92, 0.86), strength=2.2); eo.data.materials.append(tm); eo.visible_shadow = False
        sets.append((eo, tv, e))
    base = np.where(np.arange(N) % 6 == 0, np.where(verm, 6.5, 5.0), np.where(verm, 3.4, 2.1) + np.array(u['sz']) * 1.2)
    nextStart = SCN[u['last'] + 1][0] if u['last'] + 1 < meta['NS'] else 1e9
    pos = np.fromfile(f"{DATA}/pos_{k}.f32", np.float32).reshape(-1, N, 3)
    vis = fr['vis'][str(k)]
    r_pt = np.random.default_rng(100 + k).random(N)         # when each point leaves its object (and comes back to it)
    units[k] = dict(u=u, po=po, pm=pv, sets=sets, base=base, nextStart=nextStart, pos=pos, vis={v['f']: (i, v) for i, v in enumerate(vis)}, r=r_pt, t0=u['t0'],
                    prop=np.array([SOURCES[u['slot']][i % len(SOURCES[u['slot']])] for i in range(N)]))

# ---- the flat details of the pages, hung in the room at the sculpture; a film on a wall
def plane(name):
    bpy.ops.mesh.primitive_plane_add(size=1); o = bpy.context.active_object; o.name = name; o.visible_shadow = False
    m = bpy.data.materials.new(name); m.use_nodes = True; t = m.node_tree; t.nodes.clear()
    img = bpy.data.images.new(name + 'img', 4, 4)
    tx = t.nodes.new('ShaderNodeTexImage'); tx.image = img; tx.interpolation = 'Linear'; img.alpha_mode = 'STRAIGHT'
    em = t.nodes.new('ShaderNodeEmission'); tr = t.nodes.new('ShaderNodeBsdfTransparent'); mx = t.nodes.new('ShaderNodeMixShader'); out = t.nodes.new('ShaderNodeOutputMaterial')
    st = t.nodes.new('ShaderNodeValue'); st.outputs[0].default_value = 3.0
    t.links.new(tx.outputs['Color'], em.inputs['Color']); t.links.new(st.outputs[0], em.inputs['Strength'])
    t.links.new(tx.outputs['Alpha'], mx.inputs['Fac']); t.links.new(tr.outputs[0], mx.inputs[1]); t.links.new(em.outputs[0], mx.inputs[2]); t.links.new(mx.outputs[0], out.inputs['Surface'])
    o.data.materials.append(m)
    return o, img, st
ovl_o, ovl_img, ovl_st = plane('ovl')
ovl_o.scale = (1920 * SC, 1080 * SC, 1)
wall_o, wall_img, wall_st = plane('wall'); wall_st.outputs[0].default_value = 0.30
wall_o.location = (0.0, 1.35, Z + 0.62); wall_o.scale = (1.9, 1.07, 1); wall_o.rotation_euler = (math.pi / 2, 0, 0)
WALL_SLOTS = {1: 0}      # the types: the constellations film plays on the wall of the studio

def set_image(img, path):
    if os.path.exists(path):
        if img.source != 'FILE': img.source = 'FILE'
        if img.filepath != path: img.filepath = path
        img.reload()

def set_frame(f):
    t = f / FPS
    c = fr['cam'][f]
    # ---- the place: the HDRI of the slot we are in and of the one we fly to, mixed in the middle of the flight
    a, b = c['slotA'], c['slotB']
    ea, eb = SLOTENV[a], SLOTENV[b]
    mixf = smooth((c['u'] - 0.35) / 0.3) if (ea != eb and c['u'] > 0) else 0.0
    envA.image, envB.image = IMGS[ea], IMGS[eb]
    mapA.inputs['Rotation'].default_value[2] = math.radians(ENVS[ea][4] + 2.5 * t); mapB.inputs['Rotation'].default_value[2] = math.radians(ENVS[eb][4] + 2.5 * t)      # the place drifts slowly: its lights, its city, its sky move
    mixc.inputs[0].default_value = mixf
    bgL.inputs['Strength'].default_value = ENVS[ea][2] * (1 - mixf) + ENVS[eb][2] * mixf
    bgC.inputs['Strength'].default_value = ENVS[ea][3] * (1 - mixf) + ENVS[eb][3] * mixf
    # ---- the camera: the engine's move around the anchor of the slot, flying to the next one (higher arc, a roll, between two places)
    posA = np.array(ANCH[a]) + to_room(a, c['relA']); tgA = np.array(ANCH[a])
    roll = c['roll']
    if a != b and c['u'] > 0:
        e = smooth(c['u']); posB = np.array(ANCH[b]) + to_room(b, c['relB']); tgB = np.array(ANCH[b])
        pos = posA * (1 - e) + posB * e + np.array([0, 0, 0.14 * math.sin(math.pi * e)]); tg = tgA * (1 - e) + tgB * e
        roll += 0.12 * math.sin(math.pi * e) * (1 if (a + b) % 2 else -1)
    else:
        pos, tg = posA, tgA
    pos = pos + np.array([0.003 * math.sin(t * 1.1), 0, 0.002 * math.cos(t * 0.9)])
    cam.location = pos
    d = mathutils.Vector(tg - pos); q = d.to_track_quat('-Z', 'Y')
    cam.rotation_euler = (q.to_matrix().to_4x4() @ mathutils.Matrix.Rotation(roll, 4, 'Z')).to_euler(); cam.data.dof.focus_distance = float(d.length)
    # ---- the objects: where each is now (matrices), for the points that leave from it and come back to it
    def lift(t0, t1, t2, t3): return smooth((t - t0) / (t1 - t0)) * (1 - smooth((t - t2) / (t3 - t2)))
    e_ph = lift(13.2, 14.2, 18.9, 19.9); A2 = np.array(ANCH[2]); loc0, rot0, sc0 = REST['phone']
    phone.location = np.array(loc0) * (1 - e_ph) + A2 * e_ph + np.array([0, 0, 0.012 * math.sin(t * 2.0) * e_ph])
    phone.rotation_euler = (rot0[0] * (1 - e_ph), 0, rot0[2] * (1 - e_ph) + 0.25 * math.sin(t * 0.8) * e_ph)
    e_sp = lift(18.9, 19.9, 22.0, 23.0); loc0, rot0, sc0 = REST['spec']
    spec.location = np.array(loc0) * (1 - e_sp) + A2 * e_sp + np.array([0, 0, 0.01 * math.sin(t * 2.3) * e_sp])
    spec.rotation_euler = (rot0[0], rot0[1], rot0[2] + (t - 19.0) * 0.9 * e_sp); spec.scale = tuple(np.array(sc0) * (1 + 0.9 * e_sp))
    e_bk = lift(2.3, 3.4, 12.0, 13.2); loc0, rot0, sc0 = REST['book']
    book.location = (loc0[0], loc0[1], loc0[2] + 0.035 * e_bk + 0.004 * math.sin(t * 1.7) * e_bk); book.rotation_euler = (rot0[0], rot0[1], rot0[2] + 0.35 * math.sin((t - 2.3) * 0.5) * e_bk)
    loc0, rot0, sc0 = REST['plant']
    plant.rotation_euler = (rot0[0] + 0.02 * math.sin(t * 1.3), rot0[1] + 0.015 * math.sin(t * 1.1 + 1), rot0[2])
    bpy.context.view_layer.update()
    MW = {k: np.array(o.matrix_world) for k, o in PROPS.items()}
    # ---- the sculptures: the points leave their objects, form the sculpture, and go back
    glow_pos = np.zeros(3); glow_w = 0.0
    dis = {k: 0.0 for k in PROPS}
    for k, U in units.items():
        po = U['po']; slot = U['u']['slot']
        v = U['vis'].get(f)
        po.hide_render = v is None
        for eo, tv, ed in U['sets']: eo.hide_render = True
        if v is None: continue
        i, vv = v
        st0 = U['t0'] - 0.8 if U['t0'] > 0 else -0.2
        fscal = smooth((t - st0) / (1.4 if U['t0'] <= 0 else 1.1))
        uscal = smooth((t - (U['nextStart'] - 0.1)) / 1.0) if U['nextStart'] < 1e8 else 0.0
        ei = smoothv((t - st0) / (1.4 if U['t0'] <= 0 else 1.1) * 1.7 - U['r'] * 0.7) * (1 - smoothv(((t - (U['nextStart'] - 0.1)) / 1.0) * 1.7 - (1 - U['r']) * 0.7))
        if ei.max() <= 0.003: po.hide_render = True; continue
        P = U['pos'][i].astype(np.float64)
        cs, sn = math.cos(vv['psi']), math.sin(vv['psi'])
        X = P[:, 0] * cs + P[:, 2] * sn; Zl = -P[:, 0] * sn + P[:, 2] * cs; Y = P[:, 1]
        loc = np.stack([X, -Zl, Y], 1) * SC
        loc = np.stack([loc[:, 0] * math.cos(THETA[slot]) - loc[:, 1] * math.sin(THETA[slot]), loc[:, 0] * math.sin(THETA[slot]) + loc[:, 1] * math.cos(THETA[slot]), loc[:, 2]], 1) + np.array(ANCH[slot])
        # where each point comes from: the surface of its object (moving with it)
        O = np.empty_like(loc)
        for pk in set(U['prop']):
            m = U['prop'] == pk; Mw = MW[pk]
            O[m] = SAMPLES_LOCAL[pk][m] @ Mw[:3, :3].T + Mw[:3, 3]
        wob = np.stack([np.sin(U['r'] * 40 + t * 3), np.cos(U['r'] * 31 + t * 2.3), np.sin(U['r'] * 17 - t * 2.7)], 1) * (np.sin(np.pi * ei) * 0.035)[:, None]
        pos_i = O + (loc - O) * ei[:, None] + wob
        r = (U['base'] * SC * (0.18 + 0.30 * ei))[:, None, None]
        verts = (pos_i[:, None, :] + r * IV[None]).reshape(-1, 3).astype(np.float32)
        po.data.vertices.foreach_set('co', verts.reshape(-1)); po.data.update()
        po.hide_render = False
        U['pm'].outputs[0].default_value = 18.0
        scal = fscal * (1 - uscal)
        for si, (eo, tv, ed) in enumerate(U['sets']):
            w = (1 - vv['mB'] if vv['pb'] >= 0 else 1.0) if si == vv['pa'] else (vv['mB'] if si == vv['pb'] else 0.0)
            if w * scal < 0.03: continue
            eo.hide_render = False
            A_, B_ = pos_i[ed[:, 0]], pos_i[ed[:, 1]]
            grown = smoothv((np.minimum(ei[ed[:, 0]], ei[ed[:, 1]]) - 0.55) / 0.4)[:, None]      # a thread only exists between two points that have reached the sculpture: it grows from one
            B_ = A_ + (B_ - A_) * grown
            dvec = B_ - A_; dl = np.linalg.norm(dvec, axis=1, keepdims=True) + 1e-9; dn = dvec / dl
            ref = np.where(np.abs(dn[:, 2:3]) < 0.9, np.array([[0, 0, 1.0]]), np.array([[1.0, 0, 0]]))
            n1 = np.cross(dn, ref); n1 /= np.linalg.norm(n1, axis=1, keepdims=True) + 1e-9; n2 = np.cross(dn, n1)
            rad = 0.00030
            offs = [n1 * rad, (-0.5 * n1 + 0.866 * n2) * rad, (-0.5 * n1 - 0.866 * n2) * rad]
            V = np.stack([A_ + offs[0], A_ + offs[1], A_ + offs[2], B_ + offs[0], B_ + offs[1], B_ + offs[2]], 1).reshape(-1, 3)
            eo.data.vertices.foreach_set('co', V.astype(np.float32).reshape(-1)); eo.data.update()
            tv.outputs[0].default_value = 2.2 * w * scal
        glow_pos += np.array(ANCH[slot]) * scal; glow_w += scal
        for pk in set(SOURCES[slot]):
            if HERO.get(slot) and pk in HERO[slot]: continue
            dis[pk] = max(dis[pk], 0.82 * scal)
    for pk, vals in DISSOLVE.items():
        thr = 0.22 + 0.56 * min(1.0, dis[pk])
        for val in vals: val.outputs[0].default_value = thr if dis[pk] > 0.004 else 0.0
    spill.location = (glow_pos / glow_w) if glow_w > 0 else (0, 0, Z + 0.3)
    spill.data.energy = 22.0 * min(1.5, glow_w)
    sa = lift(31.0, 31.8, 35.8, 36.6)
    lamp_l.data.energy = 70 * (1 + 1.2 * sa * (math.sin(t * 6.5) ** 2) + 0.6 * lift(44.5, 45.2, 49.5, 50.6)) * (1 - 0.8 * dis['lamp'])
    # ---- the flat details of the page in use, at its sculpture; they face the room (and half the camera)
    kk = 0
    for i, sc in enumerate(SCN):
        if t >= sc[0]: kk = i
    slot = SLOT[kk]; anchor = np.array(ANCH[slot])
    n_fix = rz((0, -1, 0), THETA[slot]); n_cam = (pos - anchor); n_cam /= np.linalg.norm(n_cam) + 1e-9
    nrm = n_fix * 0.5 + n_cam * 0.5; nrm /= np.linalg.norm(nrm)
    qn = mathutils.Vector(nrm).to_track_quat('Z', 'Y')
    ovl_o.rotation_euler = qn.to_euler(); up_local = np.array(qn.to_matrix() @ mathutils.Vector((0, 1, 0)))
    ovl_o.location = anchor + up_local * (-120 * SC) + nrm * 0.004
    set_image(ovl_img, os.path.join(OVL, '%05d.png' % f))
    # ---- the film on the wall (the places that have one), and the phone's screen
    wslot = WALL_SLOTS.get(a) if (a == b or c['u'] < 0.5) else WALL_SLOTS.get(b)
    wall_o.hide_render = wslot is None
    if wslot is not None:
        folder = WALLS[wslot]; set_image(wall_img, os.path.join(folder, '%05d.jpg' % min(int(t * 30), 1520)))
    set_image(scr_img, os.path.join(SCR, '%05d.jpg' % max(0, min(100, round((t - 13.75) * 12)))))

frames = [still] if still is not None else range(kit.args('from', 0, int), kit.args('to', NFR, int))
for f in frames:
    path = os.path.join(OUT, '%05d.png' % f)
    if still is None and os.path.exists(path) and os.path.getsize(path) > 0 and not os.path.exists(path + '.part'): continue
    open(path + '.part', 'w').close()
    set_frame(f); s.frame_set(f); s.render.filepath = path; bpy.ops.render.render(write_still=True)
    os.remove(path + '.part')
