import requests
import re
from bs4 import BeautifulSoup
r = requests.get("https://bookwyrm.it/search?q=harry")
soup = BeautifulSoup(r.text, 'html.parser')
links = soup.select("a[href*='/book/']")
if links:
    print(links[0]['href'])
else:
    print("No books found")
