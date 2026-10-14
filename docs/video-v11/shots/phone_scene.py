"""Version 11, the phone: a 3D phone (body and glass made with the kit), its screen the page `screens.html` rendered frame by frame at the rate of this render (an image sequence).
Transparent: laid over the page of the shot. No HDRI (the one asked from Poly Haven could not be reached): soft boxes only, hidden from the glass. A rendered illustration.
  /tmp/bpyenv/bin/python phone_scene.py -- --out frames/ --screen screens/%04d.jpg --frames 112 --width 1280 --height 720 --samples 20 [--still 40] [--x 0.72] [--arc -18 10] 
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy
import kit

kit.reset(transparent=True)
W = kit.args('width', 1280, int); H = kit.args('height', 720, int)
N = kit.args('frames', 56, int)
ph = kit.phone(kit.args('screen'), frames=N, yaw=0.0, location=(0, 0, 0))
kit.light_rig('rim', target=ph, accent='#26c6b0', scale=1.0)
for m in bpy.data.materials:
    if m.name.startswith('screen'):
        m.node_tree.nodes['Principled BSDF'].inputs['Emission Strength'].default_value = 1.9      # the screen glows a little more than the kit's default
for o in bpy.data.objects:
    if o.type == 'LIGHT':
        o.visible_glossy = False
focus = bpy.data.objects.new('focus-obj', None); bpy.context.collection.objects.link(focus); focus.location = (0, 0, 0)
cam = kit.camera(lens=50, target=focus, fstop=None)
sensor_h = 36.0 * H / max(W, H)
dist = 0.1475 * 50 / (0.84 * sensor_h)
cam.data.shift_x = -(kit.args('x', 0.72, float) - 0.5) * (max(W, H) / W) if W >= H else 0.0
a0 = kit.args('arc0', -18, float); a1 = kit.args('arc1', 10, float)
kit.move(cam, 'orbit', radius=(dist * 1.05, dist * 0.95), arc=(a0, a1), height=(0.012, 0.0))
kit.render(kit.args('out', 'frames'))
