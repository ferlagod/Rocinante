import requests
import os
from bs4 import BeautifulSoup
import re

url = os.environ.get("BW_INSTANCE_URL", "https://bookwyrm.it").rstrip('/')
user = os.environ.get("BW_USERNAME", "Rocinante")
pwd = os.environ.get("BW_PASSWORD", "9GuuR4Ezi3er2fG")

s = requests.Session()
r = s.get(url + "/login")
soup = BeautifulSoup(r.text, 'html.parser')
csrf = soup.find('input', {'name': 'csrfmiddlewaretoken'})
if csrf:
    csrf = csrf['value']
else:
    print("No CSRF found")
    exit(1)

r2 = s.post(url + "/login", data={
    'csrfmiddlewaretoken': csrf,
    'username': user,
    'password': pwd
}, headers={'Referer': url + "/login"})
print("Login status code:", r2.status_code)
print("Is Logged in?", "Log Out" in r2.text or "logout" in r2.text.lower())
