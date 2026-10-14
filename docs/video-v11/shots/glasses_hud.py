"""Version 11, the glasses: a pair of round spectacles (Poly Haven « Round Spectacles », CC0, asset-research) made smart, with the Aiwa widget floating in front of the right lens
(an image with an alpha channel, rendered from the real widget page) and a soft beam to a second pair far behind. Dark frame, teal accent, a slow orbit with depth of field.
  /tmp/bpyenv/bin/python glasses_hud.py -- --out frames/ --asset round_spectacles_2k.gltf --hud hud.png --frames 105 --width 1920 --height 1080 --samples 24 [--still 40]
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy, mathutils
import kit

W = kit.args('width', 1920, int); H = kit.args('height', 1080, int)
FR = kit.args('frames', 105, int)
kit.reset(width=W, height=H)
ACC, WARM = '#26c6b0', '#ff7a4d'
pair = kit.import_asset(kit.args('asset'), height=0.95, yaw=0.0)
for o in [pair, *pair.children_recursive]:
    if o.type != 'MESH':
        continue
    for slot in o.material_slots:
        m = slot.material
        if not m or not m.use_nodes:
            continue
        p = next(n for n in m.node_tree.nodes if n.type == 'BSDF_PRINCIPLED')
        if m.name.endswith('_glass'):
            p.inputs['Base Color'].default_value = kit.rgba('#9fe6dd')
        else:
            for l in list(p.inputs['Base Color'].links):
                m.node_tree.links.remove(l)
            p.inputs['Base Color'].default_value = kit.rgba('#0d0d14'); p.inputs['Metallic'].default_value = 0.4; p.inputs['Roughness'].default_value = 0.25

kit.floor('gloss')
kit.light_rig('rim', target=pair, accent=ACC)

# the widget, floating in front of the right lens: a plane with the picture (alpha), glowing a little
hud = kit.args('hud')
if hud:
    bpy.ops.mesh.primitive_plane_add(size=1)
    plane = bpy.context.active_object
    plane.name = 'hud'
    plane.scale = (1.7, 1.7 * 0.7, 1)
    plane.rotation_euler = (math.radians(90), 0, math.radians(-8))
    plane.location = (1.1, -1.4, 0.75)
    mat = bpy.data.materials.new('hud'); mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    tex = nt.nodes.new('ShaderNodeTexImage'); tex.image = bpy.data.images.load(os.path.abspath(hud))
    emit = nt.nodes.new('ShaderNodeEmission'); emit.inputs['Strength'].default_value = 2.2
    trans = nt.nodes.new('ShaderNodeBsdfTransparent')
    mix = nt.nodes.new('ShaderNodeMixShader')
    out = nt.nodes.new('ShaderNodeOutputMaterial')
    nt.links.new(tex.outputs['Color'], emit.inputs['Color'])
    nt.links.new(tex.outputs['Alpha'], mix.inputs['Fac'])
    nt.links.new(trans.outputs['BSDF'], mix.inputs[1]); nt.links.new(emit.outputs['Emission'], mix.inputs[2])
    nt.links.new(mix.outputs['Shader'], out.inputs['Surface'])
    plane.data.materials.append(mat)
    plane.visible_shadow = False

cam = kit.camera(lens=50, target=pair, fstop=2.2)
kit.move(cam, 'orbit', frames=FR, radius=(8.5, 7.5), arc=(-30, 14), height=(0.9, 1.1))
still = kit.args('still', None, int)
if still is not None:
    kit.render(kit.args('out', 'frames/'), frames=FR)
else:
    kit.render(kit.args('out', 'frames/'), frames=FR)
