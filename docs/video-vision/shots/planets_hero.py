"""The Earth and Mars of the global film (« Terre. Mars. En même temps. », docs/video-vision/video.html), in 3D: two planets made of procedural textures (no image of a real planet),
a sun from the upper left, an orthographic camera that frames the film at 100 pixels to the unit, so that each planet lies exactly where the page drew its disc. Transparent: laid over the
page (which, with ?noplanets=1, keeps the labels, the nodes and the route). A rendered illustration.

  python3.13 planets_hero.py -- --out frames/ --frames 90 --samples 24 [--still 40] [--width 1920 --height 1080]
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy
import kit

W = kit.args('width', 1920, int); H = kit.args('height', 1080, int)
kit.reset(width=W, height=H, transparent=True)
N = kit.args('frames', 90, int)
# the page draws the planets at (989, 348) radius 75 px and (989, 703) radius 60 px on a 1920 x 1080 frame; 100 px = 1 unit, the frame centre is the origin
px = lambda x, y: ((x - 960) / 100.0, 0.0, (540 - y) / 100.0)      # on the 1920 x 1080 frame, whatever the size rendered
earth = kit.planet('earth', radius=0.75, location=px(989, 348), spin=(34, 0), tilt=-18, frames=N, seed=0.3)
mars = kit.planet('mars', radius=0.60, location=px(989, 703), spin=(22, 0), tilt=12, frames=N, seed=1.1)
kit.sun(direction=(0.55, 0.7, -0.45), strength=kit.args('sun', 2.6, float))
bpy.ops.object.camera_add(location=(0, -12, 0), rotation=(math.pi / 2, 0, 0)); cam = bpy.context.active_object; bpy.context.scene.camera = cam
cam.data.type = 'ORTHO'; cam.data.ortho_scale = 19.2   # the frame is 19.2 units wide: 100 px to the unit at 1920
bpy.context.scene.render.use_border = True                                      # only the part of the frame that holds the planets is rendered (the rest stays empty)
bpy.context.scene.render.border_min_x, bpy.context.scene.render.border_max_x = 820 / 1920, 1160 / 1920
bpy.context.scene.render.border_min_y, bpy.context.scene.render.border_max_y = 1 - 800 / 1080, 1 - 230 / 1080
bpy.context.scene.render.use_crop_to_border = False
kit.render(kit.args('out', 'frames'))
