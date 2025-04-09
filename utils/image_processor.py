import os
import io
import json
from PIL import Image, ImageDraw, ImageFont, ImageOps
from PIL.ExifTags import TAGS, GPSTAGS
import base64
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def add_geotag_to_image(image_path, latitude, longitude, location_name=None):
    """
    A simplified function to add location information to images 
    and create a visual watermark showing the location data
    """
    try:
        logger.info(f"Processing image at path: '{image_path}'")
        
        # Basic validation
        if not image_path:
            logger.error("Empty file path received")
            return {'success': False, 'message': 'Empty file path received'}
            
        # Normalize path
        image_path = os.path.abspath(os.path.normpath(image_path))
        logger.info(f"Normalized path: {image_path}")
        
        # Check if file exists
        if not os.path.exists(image_path):
            logger.error(f"File does not exist at path: {image_path}")
            return {'success': False, 'message': f'File does not exist at path: {image_path}'}
            
        # Store location data
        location_data = {
            "latitude": latitude,
            "longitude": longitude,
            "location_name": location_name or "Unknown location",
            "image_path": image_path,
            "original_filename": os.path.basename(image_path)
        }
        
        # Create a metadata sidecar file
        json_path = f"{image_path}.geolocation.json"
        logger.info(f"Creating metadata file at: {json_path}")
        
        with open(json_path, 'w') as f:
            json.dump(location_data, f, indent=2)
        
        # Create a visually geotagged version of the image
        try:
            # Open the image
            with Image.open(image_path) as img:
                width, height = img.size
                logger.info(f"Image opened successfully. Size: {width}x{height}")
                
                # Create a copy for editing
                img_with_tag = img.copy()
                
                # Create a semi-transparent bar at the bottom for text
                overlay = Image.new('RGBA', (width, 40), (0, 0, 0, 180))
                
                # Get a drawing context
                draw = ImageDraw.Draw(overlay)
                
                # Try to load a font, falling back to default if needed
                try:
                    # Try common system fonts
                    font_paths = [
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                        "/usr/share/fonts/ttf/dejavu/DejaVuSans.ttf",
                        "/usr/share/fonts/TTF/dejavu/DejaVuSans.ttf",
                        "DejaVuSans.ttf",
                        "Arial.ttf",
                        "arial.ttf"
                    ]
                    
                    font = None
                    for font_path in font_paths:
                        try:
                            if os.path.exists(font_path):
                                font = ImageFont.truetype(font_path, 16)
                                break
                        except:
                            continue
                    
                    if font is None:
                        font = ImageFont.load_default()
                
                except Exception:
                    # Fallback to default font
                    font = ImageFont.load_default()
                
                # Format location information
                location_text = f"{location_name} ({latitude:.6f}, {longitude:.6f})"
                    
                # Add location text to the image overlay
                text_x = 10
                text_y = 10  # Center text vertically in the bar
                draw.text((text_x, text_y), location_text, font=font, fill=(255, 255, 255, 255))
                
                # Convert overlay to the same mode as the original image
                if img_with_tag.mode != 'RGBA':
                    # Convert overlay to the same mode as the original
                    overlay = overlay.convert(img_with_tag.mode)
                
                # Create a mask for blending
                mask = Image.new('L', overlay.size, 255)  # 255 is fully opaque
                
                # Calculate position to place the overlay at the bottom of the image
                position = (0, height - 40)
                
                # Paste the overlay onto the image
                if img_with_tag.mode == 'RGBA':
                    # For RGBA images, we can use alpha compositing
                    box = (0, height - 40, width, height)
                    cropped_overlay = overlay.crop((0, 0, width, 40))
                    img_with_tag.alpha_composite(cropped_overlay, dest=position)
                else:
                    # For other modes, use paste with mask
                    img_with_tag.paste(overlay, position, mask)
                
                # Save the geotagged version
                output_filename = f"{os.path.splitext(image_path)[0]}_geotagged{os.path.splitext(image_path)[1]}"
                img_with_tag.save(output_filename)
                logger.info(f"Created geotagged image at: {output_filename}")
                
                # Successfully created both metadata and visual geotagged image
                return {
                    'success': True,
                    'message': 'Geotag added successfully',
                    'metadata_file': json_path,
                    'geotagged_image': output_filename,
                    'location_data': location_data
                }
                
        except Exception as img_error:
            logger.error(f"Error creating visual geotag: {str(img_error)}")
            # Return partial success since we at least created the metadata
            return {
                'success': True,  # Still consider this a success if metadata was saved
                'message': 'Location data saved, but visual geotag failed',
                'warning': str(img_error),
                'metadata_file': json_path,
                'location_data': location_data
            }
    
    except Exception as e:
        logger.error(f"Error in geotag process: {str(e)}")
        return {'success': False, 'message': f'Error adding geotag: {str(e)}'}
