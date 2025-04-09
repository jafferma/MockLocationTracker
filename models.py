from flask_sqlalchemy import SQLAlchemy
from datetime import datetime

db = SQLAlchemy()

class Image(db.Model):
    __tablename__ = 'images'
    
    id = db.Column(db.Integer, primary_key=True)
    filename = db.Column(db.String(255), nullable=False)
    unique_filename = db.Column(db.String(255), nullable=False, unique=True)
    lat = db.Column(db.Float, nullable=False)
    lng = db.Column(db.Float, nullable=False)
    location_name = db.Column(db.Text, nullable=False)
    timestamp = db.Column(db.String(20), nullable=False)
    path = db.Column(db.String(512), nullable=False)
    base64 = db.Column(db.Text, nullable=True)  # For storing image data for display
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    def to_dict(self):
        return {
            'id': self.id,
            'filename': self.filename,
            'unique_filename': self.unique_filename,
            'lat': self.lat,
            'lng': self.lng,
            'location_name': self.location_name,
            'timestamp': self.timestamp,
            'path': self.path,
            'base64': self.base64,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }

class LocationHistory(db.Model):
    """Model for storing previously selected locations"""
    __tablename__ = 'location_history'
    
    id = db.Column(db.Integer, primary_key=True)
    lat = db.Column(db.Float, nullable=False)
    lng = db.Column(db.Float, nullable=False)
    location_name = db.Column(db.Text, nullable=False)
    use_count = db.Column(db.Integer, default=1)  # How many times this location was used
    is_favorite = db.Column(db.Boolean, default=False)  # Allow users to mark favorite locations
    last_used = db.Column(db.DateTime, default=datetime.utcnow)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    
    # Add a unique constraint on lat/lng to avoid duplicates
    __table_args__ = (
        db.UniqueConstraint('lat', 'lng', name='unique_location'),
    )
    
    def to_dict(self):
        return {
            'id': self.id,
            'lat': self.lat,
            'lng': self.lng,
            'location_name': self.location_name,
            'use_count': self.use_count,
            'is_favorite': self.is_favorite,
            'last_used': self.last_used.isoformat() if self.last_used else None,
            'created_at': self.created_at.isoformat() if self.created_at else None
        }