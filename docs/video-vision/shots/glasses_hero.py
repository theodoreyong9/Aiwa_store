"""The 3D shot of the global film, « Après la voix, le regard » (docs/video-vision/vision.js): a pair of glasses made smart, and a beam to another pair. Written for THIS film, from the
catalogue (asset-research): a CC0 model (Poly Haven « Round Spectacles ») transformed here: a dark frame, an optical module that glows on the temple, a second pair far behind, a beam
between them, a studio HDRI (Poly Haven « Ferndale Studio 02 ») for the reflections, a camera with depth of field that circles slowly.

  python3.13 glasses_hero.py -- --out frames/ --asset round_spectacles_2k.gltf --hdri ferndale_studio_02_1k.hdr --frames 114 --width 1920 --height 1080 --samples 24 [--still 40]
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit

W = kit.args('width', 1920, int); H = kit.args('height', 1080, int)
portrait = H > W
kit.reset()
ACCENT, WARM = '#26c6b0', '#ff7a4d'
pairA = kit.import_asset(kit.args('asset'), height=0.95, yaw=0.0)        # height of the frame in metres: the pair is about 3.3 m wide, a big hero shot

# the frame goes dark and a little metallic (the textures stay for the relief); the glass is kept
for o in [pairA, *pairA.children_recursive]:
    if o.type != 'MESH': continue
    for slot in o.material_slots:
        m = slot.material
        if not m or not m.use_nodes: continue
        p = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
        if m.name.endswith('_glass'):
            p.inputs['Base Color'].default_value = kit.rgba('#a8d8d4')
        else:
            for l in list(p.inputs['Base Color'].links): m.node_tree.links.remove(l)
            p.inputs['Base Color'].default_value = kit.rgba('#0d0d14'); p.inputs['Metallic'].default_value = 0.35; p.inputs['Roughness'].default_value = 0.28


def duplicate(root, location, yaw, scale):
    new = bpy.data.objects.new('asset-b', None); bpy.context.collection.objects.link(new)
    for o in root.children_recursive:
        c = o.copy()
        if o.data is not None: c.data = o.data.copy()
        bpy.context.collection.objects.link(c)
        c.parent = new; c.matrix_parent_inverse = o.matrix_parent_inverse.copy(); c.location = o.location; c.rotation_euler = o.rotation_euler; c.scale = o.scale
    new.location, new.rotation_euler, new.scale = location, (0, 0, yaw), (scale,) * 3
    return new


bpy.context.view_layer.update()
lo, hi = kit.bounds(pairA)                      # in the world, the pair not yet turned
size = hi - lo
inv = pairA.matrix_world.inverted()
# the optical module: a small block on the front of the right temple, glowing; a child of the pair, so that it follows when the pair turns
bpy.ops.mesh.primitive_cube_add(size=1); module = bpy.context.active_object; module.name = 'module'
module.scale = (0.055 * size.x, 0.075 * size.y, 0.07 * size.z)
module.data.materials.append(kit.material('module', ACCENT, emit=ACCENT, emit_strength=9.0))
module.parent = pairA
hinge = mathutils.Vector((hi.x - size.x * 0.03, lo.y + size.y * 0.1, (lo.z + hi.z) / 2 + size.z * 0.1))
module.location = inv @ hinge
module.scale = (module.scale.x / pairA.scale.x, module.scale.y / pairA.scale.y, module.scale.z / pairA.scale.z)   # the parent's scale applies to the child

pairA.rotation_euler = (0, 0, math.radians(-12))
pairB = duplicate(pairA, (pairA.location.x + 4.2, pairA.location.y + 2.6, pairA.location.z), math.radians(168), 0.62)
for c in pairB.children_recursive:
    if c.name.startswith('module'): c.data.materials[0] = kit.material('moduleB', WARM, emit=WARM, emit_strength=9.0)
    elif c.type == 'MESH':                          # the far pair is lighter, so that it reads against the dark
        for slot in c.material_slots:
            m = slot.material
            if m and m.use_nodes and not m.name.endswith('_glass'):
                mm = m.copy(); slot.material = mm
                p = next(n for n in mm.node_tree.nodes if n.type == 'BSDF_PRINCIPLED'); p.inputs['Base Color'].default_value = kit.rgba('#b9bccf'); p.inputs['Metallic'].default_value = 0.7; p.inputs['Roughness'].default_value = 0.25
bpy.context.view_layer.update()
lo2, hi2 = kit.bounds(pairB)
src = module.matrix_world.translation
kit.signal(src, ((lo2.x + hi2.x) / 2, (lo2.y + hi2.y) / 2, (lo2.z + hi2.z) / 2), color=ACCENT, count=28, size=0.05, cycles=2.0)
kit.floor('gloss', size=400, rough=0.35)
kit.hdri(kit.args('hdri'), strength=0.35, rotation=0.6, visible=False)
kit.light_rig('rim', target=pairA, accent=ACCENT, scale=1.0)

focus = bpy.data.objects.new('focus-obj', None); bpy.context.collection.objects.link(focus)
focus.location = (src.x * 0.55 + (lo2.x + hi2.x) / 2 * 0.45, src.y * 0.55 + (lo2.y + hi2.y) / 2 * 0.45, src.z)
cam = kit.camera(lens=42, target=focus, fstop=2.4)
r = (12.0, 9.8) if not portrait else (19.5, 16.0)
kit.move(cam, 'orbit', radius=r, arc=(-52, -14), height=(1.5, 1.0))
for i in range(kit._state['frames']):              # the module breathes
    t = i / max(1, kit._state['frames'] - 1)
    mat = module.data.materials[0]; sock = mat.node_tree.nodes['Principled BSDF'].inputs['Emission Strength']
    sock.default_value = 7 + 4 * math.sin(t * math.pi * 7); sock.keyframe_insert('default_value', frame=i)
kit.render(kit.args('out', 'frames'))
