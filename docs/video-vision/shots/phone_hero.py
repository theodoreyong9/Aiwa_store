"""The phone of the global film (docs/video-vision/vision.js), in 3D: the lock screen, then the window « Mon mot d'éveil ». The body and the light are made here with the kit
(video-kit/shots/blender/kit.py: phone); the SCREEN is the page of the film itself, rendered frame by frame at the rate of this render (video.html?screen=locked|wake) and
read as an image sequence: what it shows is an illustration of the interface, the phone is a 3D drawing. The film is transparent (laid over the page by assemble --clips).

  python3.13 phone_hero.py -- --out frames/ --screen screens/%04d.png --frames 57 --hdri studio.hdr --width 1280 --height 720 --samples 20 [--still 30] [--x 0.72]
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy
import kit

kit.reset(transparent=True)
W = kit.args('width', 1280, int); H = kit.args('height', 720, int)
N = kit.args('frames', 57, int)
ph = kit.phone(kit.args('screen'), frames=N, yaw=0.0, location=(0, 0, 0))
kit.hdri(kit.args('hdri'), strength=0.5, rotation=kit.args('hdrirot', 2.4, float), visible=False)
kit.light_rig('rim', target=ph, accent='#26c6b0', scale=1.0)
for o in bpy.data.objects:
    if o.type == 'LIGHT': o.visible_glossy = False      # the soft boxes light the phone but do not print themselves on the glass
focus = bpy.data.objects.new('focus-obj', None); bpy.context.collection.objects.link(focus); focus.location = (0, 0, 0)
cam = kit.camera(lens=50, target=focus, fstop=None)
# the phone fills 84 % of the height: distance = height * lens / (0.84 * sensor height); it sits at x of the frame (0.72 in landscape): the frame is shifted the other way
sensor_h = 36.0 * H / max(W, H)
dist = 0.1475 * 50 / (0.84 * sensor_h)
cam.data.shift_x = -(kit.args('x', 0.72, float) - 0.5) * (max(W, H) / W) if W >= H else 0.0
kit.move(cam, 'orbit', radius=(dist * 1.03, dist * 0.97), arc=(-16, 9), height=(0.012, 0.0))
kit.render(kit.args('out', 'frames'))
