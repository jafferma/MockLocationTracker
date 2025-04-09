import os
import io
import json
import math
from PIL import Image, ImageDraw, ImageFont, ImageOps
from PIL.ExifTags import TAGS, GPSTAGS
import base64
import logging

# Setup logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

def convert_to_dms(decimal_degrees):
    """Convert decimal degrees to degrees, minutes, seconds format"""
    degrees = int(decimal_degrees)
    minutes_with_fraction = abs(decimal_degrees - degrees) * 60
    minutes = int(minutes_with_fraction)
    seconds = (minutes_with_fraction - minutes) * 60
    return degrees, minutes, int(seconds)

def draw_rounded_rectangle(draw, xy, radius, fill):
    """Draw a rounded rectangle"""
    x1, y1, x2, y2 = xy
    draw.rectangle([(x1 + radius, y1), (x2 - radius, y2)], fill=fill)
    draw.rectangle([(x1, y1 + radius), (x2, y2 - radius)], fill=fill)
    draw.pieslice([(x1, y1), (x1 + 2 * radius, y1 + 2 * radius)], 180, 270, fill=fill)
    draw.pieslice([(x2 - 2 * radius, y1), (x2, y1 + 2 * radius)], 270, 0, fill=fill)
    draw.pieslice([(x1, y2 - 2 * radius), (x1 + 2 * radius, y2)], 90, 180, fill=fill)
    draw.pieslice([(x2 - 2 * radius, y2 - 2 * radius), (x2, y2)], 0, 90, fill=fill)

def create_location_pin(size, color=(255, 0, 0, 255)):
    """Create a simple location pin icon"""
    pin = Image.new('RGBA', size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(pin)
    
    width, height = size
    pin_width = int(width * 0.8)
    pin_height = int(height * 0.8)
    
    # Circle at the top for pin head
    circle_radius = pin_width // 2
    circle_center = (width // 2, circle_radius)
    draw.ellipse([
        (circle_center[0] - circle_radius, circle_center[1] - circle_radius),
        (circle_center[0] + circle_radius, circle_center[1] + circle_radius)
    ], fill=color)
    
    # Triangle at bottom for pin point
    triangle_height = pin_height - circle_radius
    triangle_points = [
        (width // 2 - circle_radius, circle_radius),
        (width // 2 + circle_radius, circle_radius),
        (width // 2, height)
    ]
    draw.polygon(triangle_points, fill=color)
    
    return pin

def add_geotag_to_image(image_path, latitude, longitude, location_name=None):
    """
    A function to add Google Maps-style location information stamps to images
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
        
        # Convert decimal degrees to DMS format
        lat_deg, lat_min, lat_sec = convert_to_dms(abs(latitude))
        lng_deg, lng_min, lng_sec = convert_to_dms(abs(longitude))
        
        # Format DMS coordinates
        lat_direction = "N" if latitude >= 0 else "S"
        lng_direction = "E" if longitude >= 0 else "W"
        dms_text = f"{lat_deg}° {lat_min}' {lat_sec}\" {lat_direction}, {lng_deg}° {lng_min}' {lng_sec}\" {lng_direction}  {int(latitude * 10000) / 10000}m"
        
        # Create a visually geotagged version of the image
        try:
            # Open the image
            with Image.open(image_path) as img:
                width, height = img.size
                logger.info(f"Image opened successfully. Size: {width}x{height}")
                
                # Create a copy for editing
                img_with_tag = img.copy()
                
                # Define color scheme (Google Maps style)
                google_black = (32, 33, 36, 230)  # Main background color
                google_red = (234, 67, 53, 255)   # Location pin color
                google_text = (255, 255, 255, 255)  # Text color
                google_blue = (66, 133, 244, 255)   # Google text color
                google_green = (52, 168, 83, 255)   # Google text color
                google_yellow = (251, 188, 5, 255)  # Google text color
                
                # Calculate stamping parameters
                stamp_width = min(width - 20, 500)  # Cap at 500px or image width - 20px
                stamp_height = 90  # Fixed height for the stamp
                stamp_x = width - stamp_width - 10
                stamp_y = height - stamp_height - 10
                
                # Create a new RGBA image for the stamp
                stamp = Image.new('RGBA', (stamp_width, stamp_height), (0, 0, 0, 0))
                draw = ImageDraw.Draw(stamp)
                
                # Draw the main rounded rectangle background
                draw_rounded_rectangle(draw, (0, 0, stamp_width, stamp_height), 10, google_black)
                
                # Try to load fonts, with fallbacks
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
                    
                    regular_font = bold_font = None
                    font_size_large = 16
                    font_size_small = 12
                    
                    for font_path in font_paths:
                        try:
                            if os.path.exists(font_path):
                                regular_font = ImageFont.truetype(font_path, font_size_large)
                                bold_font = ImageFont.truetype(font_path, font_size_large)
                                break
                        except:
                            continue
                    
                    if regular_font is None:
                        regular_font = bold_font = ImageFont.load_default()
                
                except Exception:
                    # Fallback to default font
                    regular_font = bold_font = ImageFont.load_default()
                
                # Create a pin icon
                pin_size = (24, 32)
                pin_icon = create_location_pin(pin_size, google_red)
                
                # Calculate positions
                pin_position = (15, stamp_height//2 - pin_size[1]//2)
                text_start_x = pin_position[0] + pin_size[0] + 10
                
                # Add pin icon
                stamp.paste(pin_icon, pin_position, pin_icon)
                
                # Add location name (first line of text)
                location_y = 15
                draw.text((text_start_x, location_y), location_name or "Unknown Location", 
                         font=bold_font, fill=google_text)
                
                # Add DMS coordinates (second line of text)
                dms_y = 40
                draw.text((text_start_x, dms_y), dms_text, 
                         font=regular_font, fill=google_text)
                
                # Add Google text at the bottom-right
                google_text_x = stamp_width - 80
                google_text_y = stamp_height - 25
                
                # Draw "Google" text with colored letters
                letter_width = 10
                draw.text((google_text_x, google_text_y), "G", font=bold_font, fill=google_blue)
                draw.text((google_text_x + letter_width, google_text_y), "o", font=bold_font, fill=google_red)
                draw.text((google_text_x + letter_width*2, google_text_y), "o", font=bold_font, fill=google_yellow)
                draw.text((google_text_x + letter_width*3, google_text_y), "g", font=bold_font, fill=google_blue)
                draw.text((google_text_x + letter_width*4, google_text_y), "l", font=bold_font, fill=google_green)
                draw.text((google_text_x + letter_width*5, google_text_y), "e", font=bold_font, fill=google_red)
                
                # Paste the stamp onto the original image
                img_with_tag.paste(stamp, (stamp_x, stamp_y), stamp)
                
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
