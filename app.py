import os
import base64
import io
import json
from flask import Flask, render_template, request, jsonify, redirect, url_for
from werkzeug.utils import secure_filename
from datetime import datetime
from utils.image_processor import add_geotag_to_image
from flask_sqlalchemy import SQLAlchemy
from flask_migrate import Migrate
from models import db, Image

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
    # Check if the post request has the file part
    if 'file' not in request.files:
        app.logger.error("Upload failed: No file part in request")
        return jsonify({'error': 'No file part'}), 400
        
    file = request.files['file']
    if file.filename == '':
        app.logger.error("Upload failed: Empty filename")
        return jsonify({'error': 'No selected file'}), 400
        
    if not allowed_file(file.filename):
        app.logger.error(f"Upload failed: File type not allowed for {file.filename}")
        return jsonify({'error': 'File type not allowed'}), 400
    
    # Get location data
    lat = request.form.get('lat')
    lng = request.form.get('lng')
    location_name = request.form.get('location_name', 'Unknown location')
    
    app.logger.info(f"Location data received: lat={lat}, lng={lng}, name={location_name}")
    
    if not lat or not lng:
        app.logger.error("Upload failed: Missing location data")
        return jsonify({'error': 'No location data provided'}), 400
    
    try:
        lat = float(lat)
        lng = float(lng)
    except ValueError:
        app.logger.error(f"Upload failed: Invalid location data format - lat={lat}, lng={lng}")
        return jsonify({'error': 'Invalid location data'}), 400
    
    # Ensure upload directory exists with proper permissions
    upload_dir = os.path.abspath(app.config['UPLOAD_FOLDER'])
    os.makedirs(upload_dir, exist_ok=True)
    app.logger.info(f"Using upload directory: {upload_dir}")
    
    # Save the file temporarily with better error handling
    try:
        filename = secure_filename(file.filename)
        timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
        unique_filename = f"{timestamp}_{filename}"
        file_path = os.path.join(upload_dir, unique_filename)
        app.logger.info(f"Saving file to: {file_path}")
        
        # Save the file
        file.save(file_path)
        
        # Verify file was saved properly
        if not os.path.exists(file_path):
            app.logger.error(f"Upload failed: File not found after saving to {file_path}")
            return jsonify({'error': 'Failed to save uploaded file (file not found)'}), 500
            
        if os.path.getsize(file_path) == 0:
            app.logger.error(f"Upload failed: File saved with zero size at {file_path}")
            os.remove(file_path)  # Clean up empty file
            return jsonify({'error': 'Failed to save uploaded file (zero size)'}), 500
            
        app.logger.info(f"File saved successfully: {file_path}, size: {os.path.getsize(file_path)} bytes")
    except Exception as e:
        app.logger.error(f"Upload failed: Error saving file - {str(e)}")
        return jsonify({'error': f'Error saving file: {str(e)}'}), 500
    
    # Add geotag to the image
    try:
        app.logger.info(f"Adding geotag to image: {file_path} at lat={lat}, lng={lng}")
        result = add_geotag_to_image(file_path, lat, lng, location_name)
        
        if not result['success']:
            app.logger.error(f"Geotagging failed: {result['message']}")
            # Clean up file if geotagging fails
            if os.path.exists(file_path):
                os.remove(file_path)
            return jsonify({'error': result['message']}), 500
            
        app.logger.info("Geotagging successful")
        
        # Read file as base64 for display
        try:
            with open(file_path, 'rb') as img_file:
                base64_data = base64.b64encode(img_file.read()).decode('utf-8')
            app.logger.info(f"File read successfully for base64 encoding: {len(base64_data)} characters")
        except Exception as e:
            app.logger.error(f"Failed to read file for base64 encoding: {str(e)}")
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
                path=file_path,
                base64=base64_data
            )
            
            # Save to database
            db.session.add(new_image)
            db.session.commit()
            app.logger.info(f"Image saved to database with ID: {new_image.id}")
            
            return jsonify({
                'success': True,
                'image_id': new_image.id,
                'message': 'Image uploaded and geotagged successfully'
            })
        except Exception as e:
            app.logger.error(f"Database error: {str(e)}")
            # Clean up file if database save fails
            if os.path.exists(file_path):
                os.remove(file_path)
            return jsonify({'error': f'Error saving to database: {str(e)}'}), 500
    
    except Exception as e:
        app.logger.error(f"Unexpected error in upload process: {str(e)}")
        # Clean up file if processing fails
        if os.path.exists(file_path):
            os.remove(file_path)
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
