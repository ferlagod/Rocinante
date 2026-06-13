import os
import sys
import re
import requests
# pyrefly: ignore [missing-import]
from bs4 import BeautifulSoup
# pyrefly: ignore [missing-import]
from dotenv import load_dotenv

load_dotenv()

URL = os.environ.get("BW_INSTANCE_URL")
USERNAME = os.environ.get("BW_USERNAME")
PASSWORD = os.environ.get("BW_PASSWORD")

if not URL or not USERNAME or not PASSWORD:
    print("Faltan variables de entorno (BW_INSTANCE_URL, BW_USERNAME, BW_PASSWORD).")
    sys.exit(1)

URL = URL.rstrip('/')

def login(session):
    print(f"[*] Intentando login en {URL} con usuario {USERNAME}...")
    r = session.get(URL + "/login")
    if r.status_code != 200:
        print(f"[!] Error al cargar la página de login (Status: {r.status_code})")
        return False

    soup = BeautifulSoup(r.text, 'html.parser')
    csrf_input = soup.find('input', {'name': 'csrfmiddlewaretoken'})
    if not csrf_input:
        print("[!] No se encontró csrfmiddlewaretoken en el formulario de login.")
        return False
    
    csrf = csrf_input['value']
    
    r_post = session.post(URL + "/login/", data={
        'csrfmiddlewaretoken': csrf,
        'localname': USERNAME,
        'password': PASSWORD
    }, headers={'Referer': URL + "/login/"})
    
    # Comprobar si hay un botón de "Log Out" o similar en la respuesta, o si redirigió
    if "logout" in r_post.text.lower() or "log out" in r_post.text.lower() or 'id="logout"' in r_post.text:
        print("[+] Login correcto.")
        return True
    else:
        print("[!] Falló el login. Revisa las credenciales.")
        return False

def check_feed_selectors(session):
    print("\n[*] Comprobando selectores del Feed (Home)...")
    r = session.get(URL + "/")
    soup = BeautifulSoup(r.text, 'html.parser')
    
    # Selectores para encontrar los bloques (status cards)
    blocks = soup.select("#feed > .block, .column.is-two-thirds > .block")
    if not blocks:
        blocks = soup.select("[data-date], .block[data-id], .is-flex.is-align-items-stretch")
    if not blocks:
        blocks = soup.select(".block:has(time):has(img)")
        
    if not blocks:
        print("[!] No se encontraron elementos de feed (bloques). ¿Cambió el DOM o el feed está vacío?")
        return False, None
    else:
        print(f"[+] Se encontraron {len(blocks)} bloques en el feed.")
        
    # Verificar selectores internos en el primer bloque encontrado que tenga contenido
    for block in blocks:
        time_el = block.select_one("time")
        if not time_el:
            breadcrumb_el = block.select_one(".breadcrumb li a")
            if not breadcrumb_el:
                continue # Probar con el siguiente bloque
        
        # Autor
        actor_link = block.select_one("[itemprop=author] a[itemprop=url], [itemprop=author] a, .status-info a, a[href*='/user/']")
        if not actor_link:
            print("[!] Selector de autor falló en un bloque válido.")
        else:
            print("[+] Selector de autor OK.")
            
        # Avatar
        avatar_img = block.select_one(".media-left img, img.avatar, img[src*=avatar]")
        if not avatar_img:
            print("[!] Selector de avatar falló.")
        else:
            print("[+] Selector de avatar OK.")
            
        # Enlace al libro (opcional, algunos estados no tienen libro, pero guardaremos uno si lo hay para la siguiente prueba)
        book_link = block.select_one("a[href*='/book/']")
        if book_link:
            book_url = book_link['href']
            if not book_url.startswith("http"):
                book_url = URL + book_url
            print(f"[+] Enlace a libro encontrado: {book_url}")
            return True, book_url
            
    print("[-] No se encontró ningún enlace a un libro en el feed. Buscando un libro de prueba...")
    r_search = session.get(URL + "/search?q=harry")
    soup_search = BeautifulSoup(r_search.text, 'html.parser')
    book_link = soup_search.select_one("a[href*='/book/']")
    if book_link:
        book_url = book_link['href']
        if not book_url.startswith("http"):
            book_url = URL + book_url
        print(f"[+] Libro encontrado a través de búsqueda: {book_url}")
        return True, book_url
    
    print("[-] Tampoco se encontró un libro por búsqueda.")
    return True, None

def check_book_page(session, book_url):
    print(f"\n[*] Comprobando regex en la página del libro: {book_url}...")
    r = session.get(book_url)
    html = r.text
    
    # Test ReviewContext: name="user" value="\d+"
    user_regex = re.compile(r'name=["\']user["\']\s+value=["\'](\d+)["\']')
    match_user = user_regex.search(html)
    if match_user:
        print(f"[+] User ID encontrado en la vista del libro: {match_user.group(1)}")
    else:
        print("[!] No se pudo encontrar el User ID con la regex en la página del libro.")
        
    # Test ProgressContext: <form name="reading-progress... id="id" value="\d+"
    progress_regex = re.compile(r'<form[^>]*name=["\']reading-progress-[^>]*>.*?name=["\']id["\'][^>]*value=["\'](\d+)["\']', re.DOTALL)
    match_progress = progress_regex.search(html)
    if match_progress:
        print(f"[+] Readthrough ID encontrado: {match_progress.group(1)}")
    else:
        print("[-] No se encontró formulario de progreso. Es normal si el libro no está en tu estantería activa.")

def check_user_profile(session):
    profile_url = f"{URL}/user/{USERNAME}"
    print(f"\n[*] Comprobando regex de User ID en el perfil / estantería: {profile_url}...")
    
    # En la app se busca en profileUrl/books/to-read
    shelf_url = f"{profile_url}/books/to-read"
    r = session.get(shelf_url)
    html = r.text
    
    user_match_regex = re.compile(r'name=["\']user["\'][^>]*?value=["\'](\d+)["\']|value=["\'](\d+)["\'][^>]*?name=["\']user["\']', re.IGNORECASE)
    match = user_match_regex.search(html)
    if match:
        user_id = match.group(1) or match.group(2)
        print(f"[+] User ID extraído correctamente de la estantería: {user_id}")
    else:
        print("[!] No se encontró el User ID en la estantería. La regex de BookWyrmRepository.getUserId() podría estar rota.")

def run_tests():
    s = requests.Session()
    s.headers.update({'User-Agent': 'Mozilla/5.0 (Rocinante Scraper Monitor)'})
    
    if not login(s):
        sys.exit(1)
        
    success, book_url = check_feed_selectors(s)
    
    if book_url:
        check_book_page(s, book_url)
        
    check_user_profile(s)
    
    print("\n[=] Pruebas finalizadas.")

if __name__ == "__main__":
    run_tests()
