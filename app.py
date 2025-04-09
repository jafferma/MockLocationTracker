import os
import base64
import io
import json
import logging
from flask import Flask, render_template, request, jsonify, redirect, url_for
from werkzeug.utils import secure_filename
from datetime import datetime
from utils.image_processor import add_geotag_to_image
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from models import db, Image

# Configure logging for better debugging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max upload size
app.config['UPLOAD_FOLDER'] = 'uploads'
app.config['ALLOWED_EXTENSIONS'] = {'png', 'jpg', 'jpeg', 'gif'}

# Configure database - make sure we have the DATABASE_URL environment variable
database_url = os.environ.get('DATABASE_URL')
if not database_url:
    raise RuntimeError("DATABASE_URL environment variable not set")

app.config['SQLALCHEMY_DATABASE_URI'] = database_url
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

# Initialize database
db.init_app(app)
migrate = Migrate(app, db)

# Create uploads folder if it doesn't exist
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

# Create all database tables
with app.app_context():
    db.create_all()

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/upload', methods=['POST'])
def upload_file():
    logger.info("Processing upload request")
    
    # Check if the post request has the file part
    if 'file' not in request.files:
        logger.error("Upload failed: No file part in request")
        return jsonify({'error': 'No file part'}), 400
        
    file = request.files['file']
    if file.filename == '':
        logger.error("Upload failed: Empty filename")
        return jsonify({'error': 'No selected file'}), 400
        
    if not allowed_file(file.filename):
        logger.error(f"Upload failed: File type not allowed for {file.filename}")
        return jsonify({'error': 'File type not allowed'}), 400
    
    # Get location data
    lat = request.form.get('lat')
    lng = request.form.get('lng')
    location_name = request.form.get('location_name', 'Unknown location')
    
    logger.info(f"Location data received: lat={lat}, lng={lng}, name={location_name}")
    
    if not lat or not lng:
        logger.error("Upload failed: Missing location data")
        return jsonify({'error': 'No location data provided'}), 400
    
    try:
        lat = float(lat)
        lng = float(lng)
    except ValueError:
        logger.error(f"Upload failed: Invalid location data format - lat={lat}, lng={lng}")
        return jsonify({'error': 'Invalid location data'}), 400
    
    # Ensure upload directory exists with proper permissions
    upload_dir = os.path.abspath(app.config['UPLOAD_FOLDER'])
    os.makedirs(upload_dir, exist_ok=True)
    logger.info(f"Using upload directory: {upload_dir}")
    
    # Save the file with better error handling
    try:
        filename = secure_filename(file.filename)
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        unique_filename = f"{timestamp}_{filename}"
        file_path = os.path.join(upload_dir, unique_filename)
        logger.info(f"Saving file to: {file_path}")
        
        # Save the file
        file.save(file_path)
        
        # Verify file was saved properly
        if not os.path.exists(file_path):
            logger.error(f"Upload failed: File not found after saving to {file_path}")
            return jsonify({'error': 'Failed to save uploaded file (file not found)'}), 500
            
        file_size = os.path.getsize(file_path)
        if file_size == 0:
            logger.error(f"Upload failed: File saved with zero size at {file_path}")
            os.remove(file_path)  # Clean up empty file
            return jsonify({'error': 'Failed to save uploaded file (zero size)'}), 500
            
        logger.info(f"File saved successfully: {file_path}, size: {file_size} bytes")
    except Exception as e:
        logger.error(f"Upload failed: Error saving file - {str(e)}")
        return jsonify({'error': f'Error saving file: {str(e)}'}), 500
    
    # Process the image and add location data
    try:
        logger.info(f"Adding location data to image: {file_path} at lat={lat}, lng={lng}")
        
        # Use our simplified approach to add location data
        result = add_geotag_to_image(file_path, lat, lng, location_name)
        
        if not result['success']:
            logger.error(f"Location tagging failed: {result['message']}")
            # Keep the file even if tagging fails, as it might be useful for debugging
            return jsonify({'error': result['message']}), 500
            
        logger.info(f"Location data added successfully: {result.get('sidecar_file', 'No sidecar file')}")
        
        # Use the geotagged version if it exists, otherwise use the original
        geotagged_path = f"{os.path.splitext(file_path)[0]}_geotagged{os.path.splitext(file_path)[1]}"
        display_path = geotagged_path if os.path.exists(geotagged_path) else file_path
        
        # Read the file as base64 for display
        try:
            with open(display_path, 'rb') as img_file:
                base64_data = base64.b64encode(img_file.read()).decode('utf-8')
            logger.info(f"File read successfully for base64 encoding: {len(base64_data)} chars")
        except Exception as e:
            logger.error(f"Failed to read file for base64 encoding: {str(e)}")
            # Continue without base64 data if reading fails
            base64_data = None
        
        # Create new image record in database
        try:
            new_image = Image(
                filename=filename,
                unique_filename=unique_filename,
                lat=lat, 
                lng=lng,
                location_name=location_name,
                timestamp=timestamp,
                path=display_path,  # Use the display path (with geotagged text)
                base64=base64_data
            )
            
            # Save to database
            db.session.add(new_image)
            db.session.commit()
            logger.info(f"Image saved to database with ID: {new_image.id}")
            
            # Return success with additional information
            return jsonify({
                'success': True,
                'image_id': new_image.id,
                'message': 'Image uploaded and location data added successfully',
                'location': {
                    'lat': lat,
                    'lng': lng,
                    'name': location_name
                }
            })
        except Exception as e:
            logger.error(f"Database error: {str(e)}")
            # Keep files for debugging
            return jsonify({'error': f'Error saving to database: {str(e)}'}), 500
    
    except Exception as e:
        logger.error(f"Unexpected error in upload process: {str(e)}")
        # Keep files for debugging
        return jsonify({'error': f'Error processing image: {str(e)}'}), 500

@app.route('/api/images', methods=['GET'])
def get_images():
    images = Image.query.order_by(Image.created_at.desc()).all()
    return jsonify({
        'images': [img.to_dict() for img in images]
    })

@app.route('/api/images/<int:image_id>', methods=['GET'])
def get_image(image_id):
    image = Image.query.get(image_id)
    if image:
        return jsonify(image.to_dict())
    return jsonify({'error': 'Image not found'}), 404

@app.errorhandler(413)
def too_large(e):
    return jsonify({'error': 'File too large (max 16MB)'}), 413

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000, debug=True)
