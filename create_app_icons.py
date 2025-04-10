from PIL import Image
import os

# Source icon
source_icon = "generated-icon.png"

# Target densities with their sizes
# mipmap-mdpi ~= 48x48
# mipmap-hdpi ~= 72x72
# mipmap-xhdpi ~= 96x96
# mipmap-xxhdpi ~= 144x144
# mipmap-xxxhdpi ~= 192x192
densities = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192
}

# Load the source icon
img = Image.open(source_icon)

# Create round versions for both normal and round icons
for density, size in densities.items():
    # Resize the image
    resized_img = img.resize((size, size), Image.LANCZOS)
    
    # Ensure the directory exists
    output_dir = f"android_app/app/src/main/res/mipmap-{density}"
    os.makedirs(output_dir, exist_ok=True)
    
    # Save as normal launcher icon
    resized_img.save(f"{output_dir}/ic_launcher.png")
    
    # Also save as round launcher icon (same image in this case)
    resized_img.save(f"{output_dir}/ic_launcher_round.png")

print("App icons created successfully in the mipmap directories!")