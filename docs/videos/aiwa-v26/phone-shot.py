"""The 3D shot of the Aiwa film (v26): a phone, its screen the Aiwa widget then the lock screen, lit by a real studio (Poly Haven, CC0), a slow orbit.
  /tmp/bpyenv/bin/python phone-shot.py -- --out frames/ --screen scr/%04d.jpg --hdri monochrome_studio_02_2k.hdr [--frames 65 --still 30 --width 960 --height 540 --samples 32]"""
import os, sys
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'video-kit', 'shots', 'blender'))
import bpy
import kit

kit.reset(transparent=True)
kit.hdri(kit.args('hdri'), strength=1.0, rotation=0.6, visible=False)
obj = kit.phone(kit.args('screen'), location=(0, 0, 0.0), yaw=0)
obj.location = (0, 0, 0.074)
# the glass: the screen glows more than the room reflects on it
pb = bpy.data.materials['screen'].node_tree.nodes['Principled BSDF']
pb.inputs['Emission Strength'].default_value = 3.4; pb.inputs['Roughness'].default_value = 0.55
if 'Specular IOR Level' in pb.inputs: pb.inputs['Specular IOR Level'].default_value = 0.02
cam = kit.camera(lens=62, target=obj, fstop=3.2)
kit.move(cam, 'orbit', radius=(0.58, 0.52), arc=(-28, 22), height=(0.015, -0.01))
kit.render(kit.args('out', 'frames'))
