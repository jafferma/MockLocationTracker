import os
import io
import json
import math
import urllib.request
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

def create_map_thumbnail(latitude, longitude, zoom=14, size=(120, 120)):
    """
    Create a simple map thumbnail image (fallback if can't fetch from API)
    """
    # Create a blank image with light blue background for ocean/sky
    map_img = Image.new('RGBA', size, (200, 230, 255, 255))
    draw = ImageDraw.Draw(map_img)
    
    # Draw some green rectangles for "land"
    w, h = size
    draw.rectangle([10, 30, w-10, h-10], fill=(200, 240, 190))
    
    # Draw a "road"
    draw.line([(0, h//2), (w, h//2)], fill=(255, 255, 255), width=5)
    draw.line([(w//2, 0), (w//2, h)], fill=(255, 255, 255), width=3)
    
    # Create and paste a pin in the center
    pin_size = (24, 32)
    pin = create_location_pin(pin_size, (255, 0, 0, 255))
    pin_pos = (w//2 - pin_size[0]//2, h//2 - pin_size[1])
    map_img.paste(pin, pin_pos, pin)
    
    return map_img

def try_get_static_map(latitude, longitude, zoom=14, size="120x120"):
    """
    Try to get a static map image from an open source provider
    Returns a PIL Image object or None if failed
    """
    try:
        # OpenStreetMap static map URL
        osm_url = f"https://static.osm.org/staticmap/v1/staticmap?center={latitude},{longitude}&zoom={zoom}&size={size}&markers={latitude},{longitude},red-pushpin"
        
        # Create a temporary file to save the image
        static_map_path = "temp_static_map.png"
        
        # Download the map image
        with urllib.request.urlopen(osm_url) as response, open(static_map_path, 'wb') as out_file:
            out_file.write(response.read())
        
        # Open the image with PIL
        map_img = Image.open(static_map_path)
        
        # Delete the temporary file
        os.remove(static_map_path)
        
        return map_img
    except Exception as e:
        logger.error(f"Failed to get static map: {str(e)}")
        return None

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
        dms_text = f"{lat_deg}° {lat_min}' {lat_sec}\" {lat_direction}, {lng_deg}° {lng_min}' {lng_sec}\" {lng_direction}  {int(abs(latitude) * 10000) / 10000}m"
        
        # Format decimal coordinates as used in Google Maps
        dec_text = f"{lat_deg}°{lat_min}'{lat_sec}\"{lat_direction} {lng_deg}°{lng_min}'{lng_sec}\"{lng_direction}"
                
        # Create a visually geotagged version of the image
        try:
            # Open the image
            with Image.open(image_path) as img:
                width, height = img.size
                logger.info(f"Image opened successfully. Size: {width}x{height}")
                
                # Create a copy for editing
                img_with_tag = img.copy()
                
                # Define color scheme (Google Maps style)
                google_black = (32, 33, 36, 230)    # Main background color
                google_red = (234, 67, 53, 255)     # Location pin color
                google_text = (255, 255, 255, 255)  # Text color
                google_blue = (66, 133, 244, 255)   # Google text color
                google_green = (52, 168, 83, 255)   # Google text color
                google_yellow = (251, 188, 5, 255)  # Google text color
                
                # Calculate stamping parameters - BIGGER as requested
                stamp_width = min(width - 20, 600)  # Cap at 600px or image width - 20px
                stamp_height = 150                   # Increased height for the stamp
                
                # Position in the bottom-right corner
                stamp_x = width - stamp_width - 10
                stamp_y = height - stamp_height - 10
                
                # Create a new RGBA image for the stamp
                stamp = Image.new('RGBA', (stamp_width, stamp_height), (0, 0, 0, 0))
                draw = ImageDraw.Draw(stamp)
                
                # Draw the main rounded rectangle background
                draw_rounded_rectangle(draw, (0, 0, stamp_width, stamp_height), 15, google_black)
                
                # Try to load fonts, with fallbacks
                try:
                    # Try common system fonts with larger sizes
                    font_paths = [
                        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                        "/usr/share/fonts/ttf/dejavu/DejaVuSans.ttf",
                        "/usr/share/fonts/TTF/dejavu/DejaVuSans.ttf",
                        "DejaVuSans.ttf",
                        "Arial.ttf",
                        "arial.ttf"
                    ]
                    
                    regular_font = bold_font = small_font = None
                    font_size_large = 20  # Larger font
                    font_size_medium = 16
                    font_size_small = 14
                    
                    for font_path in font_paths:
                        try:
                            if os.path.exists(font_path):
                                regular_font = ImageFont.truetype(font_path, font_size_medium)
                                bold_font = ImageFont.truetype(font_path, font_size_large)
                                small_font = ImageFont.truetype(font_path, font_size_small)
                                break
                        except:
                            continue
                    
                    if regular_font is None:
                        regular_font = bold_font = small_font = ImageFont.load_default()
                
                except Exception:
                    # Fallback to default font
                    regular_font = bold_font = small_font = ImageFont.load_default()
                
                # Try to get map thumbnail from an API or create a simple one if failed
                map_size = (120, 120)  # Larger map thumbnail
                map_img = try_get_static_map(latitude, longitude, zoom=14, size="120x120")
                
                if map_img is None:
                    # If failed to get a map, create a simple one
                    map_img = create_map_thumbnail(latitude, longitude, zoom=14, size=map_size)
                
                # Create a pin icon
                pin_size = (32, 40)  # Larger pin
                pin_icon = create_location_pin(pin_size, google_red)
                
                # Calculate positions
                # Layout:
                # [Pin] [Location Name]       [Map Thumbnail]
                #       [Coordinates]
                #       [Google Colored Text]
                
                # Position the map on the right side
                map_padding = 15
                map_pos_x = stamp_width - map_size[0] - map_padding
                map_pos_y = (stamp_height - map_size[1]) // 2
                
                # Position the pin on the left side
                pin_padding = 15
                pin_pos_x = pin_padding
                pin_pos_y = (stamp_height - pin_size[1]) // 2
                
                # Text starts after the pin
                text_start_x = pin_pos_x + pin_size[0] + 10
                text_area_width = map_pos_x - text_start_x - 10  # Space between text and map
                
                # Add the map thumbnail
                if map_img.mode != 'RGBA':
                    map_img = map_img.convert('RGBA')
                
                # Add a border to the map
                map_with_border = Image.new('RGBA', (map_size[0] + 4, map_size[1] + 4), (255, 255, 255, 180))
                map_with_border.paste(map_img, (2, 2))
                stamp.paste(map_with_border, (map_pos_x - 2, map_pos_y - 2), map_with_border)
                
                # Add pin icon
                stamp.paste(pin_icon, (pin_pos_x, pin_pos_y), pin_icon)
                
                # Add location name (first line of text)
                location_y = stamp_height // 4 - 5
                draw.text((text_start_x, location_y), location_name or "Unknown Location", 
                         font=bold_font, fill=google_text)
                
                # Add DMS coordinates (second line of text)
                dms_y = stamp_height // 2 - 10
                draw.text((text_start_x, dms_y), dec_text, 
                         font=regular_font, fill=google_text)
                
                # Add elevation or other data (third line)
                elev_y = stamp_height * 3 // 4
                draw.text((text_start_x, elev_y), f"{int(abs(latitude) * 1000) / 1000}m", 
                         font=small_font, fill=(200, 200, 200, 255))
                
                # Add Google text at the bottom-right near the map
                google_text_x = map_pos_x + map_size[0] // 2 - 40
                google_text_y = map_pos_y + map_size[1] + 5
                
                # Draw "Google" text with colored letters
                letter_width = 12
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
