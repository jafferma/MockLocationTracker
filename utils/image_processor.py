import os
import piexif
import piexif.helper
from PIL import Image
from fractions import Fraction

def convert_to_dms(coordinate):
    """
    Convert decimal coordinates to degrees, minutes, seconds for EXIF
    """
    degrees = abs(int(coordinate))
    minutes_float = (abs(coordinate) - degrees) * 60
    minutes = int(minutes_float)
    seconds = (minutes_float - minutes) * 60
    
    # Convert to EXIF format (fractions)
    degrees_fraction = (degrees, 1)
    minutes_fraction = (minutes, 1)
    seconds_fraction = (int(seconds * 100), 100)
    
    return degrees_fraction, minutes_fraction, seconds_fraction

def add_geotag_to_image(image_path, latitude, longitude, location_name=None):
    """
    Add GPS metadata to an image file
    """
    try:
        # Check if file path is valid
        if not image_path or not os.path.exists(image_path):
            return {'success': False, 'message': f'Invalid file path: {image_path}'}
            
        # Open the image to ensure it's valid
        img = Image.open(image_path)
        
        # Get original exif data or create new if none exists
        try:
            exif_dict = piexif.load(img.info.get('exif', b''))
        except (ValueError, piexif.InvalidImageDataError):
            exif_dict = {'0th': {}, 'Exif': {}, 'GPS': {}, '1st': {}, 'thumbnail': None}
        
        # Convert lat/long to DMS format
        lat_dms = convert_to_dms(latitude)
        lng_dms = convert_to_dms(longitude)
        
        # Set GPS tags
        exif_dict['GPS'] = {
            piexif.GPSIFD.GPSVersionID: (2, 0, 0, 0),
            piexif.GPSIFD.GPSLatitudeRef: 'N' if latitude >= 0 else 'S',
            piexif.GPSIFD.GPSLatitude: lat_dms,
            piexif.GPSIFD.GPSLongitudeRef: 'E' if longitude >= 0 else 'W',
            piexif.GPSIFD.GPSLongitude: lng_dms,
        }
        
        # Add location name to EXIF UserComment if provided
        if location_name:
            user_comment = piexif.helper.UserComment.dump(f"Location: {location_name}")
            exif_dict['Exif'][piexif.ExifIFD.UserComment] = user_comment
        
        # Convert to bytes and save
        exif_bytes = piexif.dump(exif_dict)
        
        # Save the image with new EXIF data
        img.save(image_path, exif=exif_bytes)
        img.close()
        
        return {'success': True, 'message': 'Geotag added successfully'}
    
    except Exception as e:
        return {'success': False, 'message': f'Error adding geotag: {str(e)}'}
