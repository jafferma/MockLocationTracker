import os
import base64
import io
import json
from flask import Flask, render_template, request, jsonify, redirect, url_for
from werkzeug.utils import secure_filename
from datetime import datetime
from utils.image_processor import add_geotag_to_image

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024 * 1024  # 16MB max upload size
app.config['UPLOAD_FOLDER'] = 'uploads'
app.config['ALLOWED_EXTENSIONS'] = {'png', 'jpg', 'jpeg', 'gif'}

# Create uploads folder if it doesn't exist
os.makedirs(app.config['UPLOAD_FOLDER'], exist_ok=True)

# In-memory storage for uploaded images and their metadata
# Structure: {image_id: {filename, lat, lng, timestamp, path}}
images_db = {}
image_counter = 0

def allowed_file(filename):
    return '.' in filename and \
           filename.rsplit('.', 1)[1].lower() in app.config['ALLOWED_EXTENSIONS']

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/upload', methods=['POST'])
def upload_file():
    global image_counter
    
    if 'file' not in request.files:
        return jsonify({'error': 'No file part'}), 400
        
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No selected file'}), 400
        
    if not allowed_file(file.filename):
        return jsonify({'error': 'File type not allowed'}), 400
    
    # Get location data
    lat = request.form.get('lat')
    lng = request.form.get('lng')
    location_name = request.form.get('location_name', 'Unknown location')
    
    if not lat or not lng:
        return jsonify({'error': 'No location data provided'}), 400
    
    try:
        lat = float(lat)
        lng = float(lng)
    except ValueError:
        return jsonify({'error': 'Invalid location data'}), 400
    
    # Save the file temporarily
    filename = secure_filename(file.filename)
    timestamp = datetime.now().strftime('%Y%m%d_%H%M%S')
    unique_filename = f"{timestamp}_{filename}"
    file_path = os.path.join(app.config['UPLOAD_FOLDER'], unique_filename)
    file.save(file_path)
    
    # Add geotag to the image
    try:
        result = add_geotag_to_image(file_path, lat, lng, location_name)
        if not result['success']:
            return jsonify({'error': result['message']}), 500
            
        # Create image record
        image_id = image_counter
        image_counter += 1
        
        # Read file as base64 for display
        with open(file_path, 'rb') as img_file:
            base64_data = base64.b64encode(img_file.read()).decode('utf-8')
        
        images_db[image_id] = {
            'id': image_id,
            'filename': filename,
            'unique_filename': unique_filename,
            'lat': lat, 
            'lng': lng,
            'location_name': location_name,
            'timestamp': timestamp,
            'path': file_path,
            'base64': base64_data
        }
        
        return jsonify({
            'success': True,
            'image_id': image_id,
            'message': 'Image uploaded and geotagged successfully'
        })
    
    except Exception as e:
        # Clean up file if geotagging fails
        if os.path.exists(file_path):
            os.remove(file_path)
        return jsonify({'error': str(e)}), 500

@app.route('/api/images', methods=['GET'])
def get_images():
    return jsonify({
        'images': list(images_db.values())
    })

@app.route('/api/images/<int:image_id>', methods=['GET'])
def get_image(image_id):
    if image_id in images_db:
        return jsonify(images_db[image_id])
    return jsonify({'error': 'Image not found'}), 404

@app.errorhandler(413)
def too_large(e):
    return jsonify({'error': 'File too large (max 16MB)'}), 413

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8000, debug=True)
