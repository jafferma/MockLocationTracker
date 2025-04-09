"""
Database migration script to create and update tables as needed.
Run this file directly to apply migrations: python migrations.py
"""

import os
from app import app, db
from models import Image, LocationHistory
from datetime import datetime

def run_migrations():
    """Run all database migrations"""
    with app.app_context():
        # Create tables if they don't exist
        db.create_all()
        
        print("Migrations completed successfully.")

if __name__ == "__main__":
    # Run migrations when script is executed directly
    run_migrations()