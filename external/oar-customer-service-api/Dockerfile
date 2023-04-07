# Use an official Python runtime as a parent image
FROM python:3.9-slim-buster

# Set the working directory to /app and set PYTHONPATH to /app
ENV PYTHONPATH=/app
WORKDIR /app

# Copy the requirements file into the container at /app
COPY requirements.txt /app

# Create a virtual environment and activate it
# This will avoid getting message: WARNING: Running pip as the 'root'...
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN . /opt/venv/bin/activate

# Install any needed packages specified in requirements.txt
RUN pip install --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Copy the rest of the app code into the container at /app
COPY . /app

# Expose port 9595 for the app
EXPOSE 9595

# Start the app when the container is run
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "9595"]
