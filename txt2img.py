import webuiapi
import argparse

parser = argparse.ArgumentParser(description='Convert image to text')
parser.add_argument('prompt', help='Prompt for the model')
parser.add_argument('--width', default=512, help='Width of the image')
parser.add_argument('--height', default=512, help='Height of the image')
parser.add_argument('--negative', default="", help='Negative prompt, completely optional')
parser.add_argument('--model', default="realisticVision", help='Model to use for image conversion')
parser.add_argument('--steps', default=20, help='Number of steps to run')
parser.add_argument('--scale', default=7, help='Scale of the image')
parser.add_argument('--denoising-strength', type=float, default=0.5, help='Strength of denoising (default: 0.5)')
parser.add_argument('--sampler', default="Euler a", help='Sampler to use (default: Euler a)')
parser.add_argument('--filename', default="output", help='Filename to save the image to, without extension (default: output)')

args = parser.parse_args()

api = webuiapi.WebUIApi(host='127.0.0.1', port=7860)

models = {
    "hassanBlend": "HassanBlend1.5-pruned.safetensors [9933cd34f5]",
    "realisticVision": "realisticVisionV20_v20.safetensors [c0d1994c73]",
    "illuminatiDiffusion": "illuminatiDiffusionV1_v11.safetensors [cae1bee30e]",
    "urpm": "urpm.ckpt [33b0c0e84c]",
    "f222": "f222.ckpt",
}
try:
    model = models[args.model]
except KeyError:
    print("Valid models are:")
    for key in models:
        print(key)
    exit(1)

options = {'sd_model_checkpoint': model}
api.set_options(options)

restore_faces = "face" in args.prompt # TODO parse prompt for faces
result1 = api.txt2img(prompt=args.prompt,
                      negative_prompt=args.negative,
                      seed=-1,
                      cfg_scale=args.scale,
                      steps=args.steps,
                      denoising_strength=args.denoising_strength,
                      sampler_index=args.sampler,
                      width=args.width,
                      height=args.height,
                      restore_faces=restore_faces,
                      #                      enable_hr=True,
                      #                      hr_scale=2,
                      #                      hr_upscaler=webuiapi.HiResUpscaler.Latent,
                      #                      hr_second_pass_steps=20,
                      #                      hr_resize_x=1536,
                      #                      hr_resize_y=1024,
                      )
api.util_wait_for_ready()
filename = args.filename + ".png"
result1.image.save(filename)
print("Saved image to " + filename)
