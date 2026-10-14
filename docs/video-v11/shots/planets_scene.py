"""Version 11, Earth and Mars: two planets made of procedural textures (no image of a real planet), a sun from the upper left, a perspective camera that slowly comes in,
and a signal (glowing beads) that travels from one to the other: the delay of the light. Transparent: laid over the page of the shot, which writes the words. A rendered illustration.
  /tmp/bpyenv/bin/python planets_scene.py -- --out frames/ --frames 84 --width 1600 --height 900 --samples 20 [--still 40]
"""
import math, os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy
import kit

W = kit.args('width', 1600, int); H = kit.args('height', 900, int)
N = kit.args('frames', 84, int)
kit.reset(width=W, height=H, transparent=True)
earth = kit.planet('earth', radius=1.75, location=(-2.9, 0.0, -0.35), spin=(40, 0), tilt=-16, frames=N, seed=0.3)
mars = kit.planet('mars', radius=0.85, location=(3.3, 0.0, 0.55), spin=(26, 0), tilt=10, frames=N, seed=1.1)
kit.sun(direction=(0.65, 0.55, -0.35), strength=kit.args('sun', 3.0, float))
kit.signal((-1.1, -0.4, 0.05), (2.5, -0.4, 0.6), color='#58d39b', count=30, size=0.03, cycles=2.0, frames=N, strength=12.0)
focus = bpy.data.objects.new('focus-obj', None); bpy.context.collection.objects.link(focus); focus.location = (0.2, 0, 0)
cam = kit.camera(lens=50, target=focus, fstop=None)
kit.move(cam, 'dolly-in', frames=N, radius=(15.5, 13.0), arc=(0, 0), height=(0.2, 0.2))
kit.render(kit.args('out', 'frames'))
