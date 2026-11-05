import json
import os
import time
from datetime import datetime, timedelta

DATA_DIR = r"D:\Coding\Pulsar\prototype\python\agent_core\data"
os.makedirs(DATA_DIR, exist_ok=True)

# 1. Generate 100 Contacts with Elder and Modern Bangladeshi Edge Cases
contacts = [
    # Kinship Edge Cases (Elder users saving directly by relation or by name)
    {"id": "c001", "name": "Tanvir Ahmed", "phone": "01711000001", "relation": "elder_son", "nicknames": ["Boro Chele", "Tanvir"], "category": "family"},
    {"id": "c002", "name": "Rifat Hasan", "phone": "01819000002", "relation": "younger_son", "nicknames": ["Choto Chele", "Rifat"], "category": "family"},
    {"id": "c003", "name": "Nabila Rahman", "phone": "01912000003", "relation": "elder_daughter", "nicknames": ["Boro Meye", "Nabila"], "category": "family"},
    {"id": "c004", "name": "Tasnim Jahan", "phone": "01615000004", "relation": "younger_daughter", "nicknames": ["Choto Meye", "Tasnim"], "category": "family"},
    {"id": "c005", "name": "Fahim Kabir", "phone": "01715000005", "relation": "son_in_law", "nicknames": ["Boro Jamai", "Fahim"], "category": "family"},
    {"id": "c006", "name": "Rokeya Begum", "phone": "01552000006", "relation": "spouse", "nicknames": ["Wife", "Bou", "Amar Ghor"], "category": "family"},
    
    # Direct Kinship Label Contacts (Elder users saving directly by relation text)
    {"id": "c007", "name": "Choto Chele", "phone": "01819000002", "relation": "younger_son", "nicknames": ["Rifat"], "category": "family"},
    {"id": "c008", "name": "Boro Meye", "phone": "01912000003", "relation": "elder_daughter", "nicknames": ["Nabila"], "category": "family"},
    {"id": "c009", "name": "Choto Jamai", "phone": "01713000009", "relation": "younger_son_in_law", "nicknames": ["Zahid"], "category": "family"},
    {"id": "c010", "name": "Sejo Chele (Kamrul)", "phone": "01814000010", "relation": "middle_son", "nicknames": ["Kamrul"], "category": "family"},
    {"id": "c011", "name": "Mejo Meye (Sadia)", "phone": "01915000011", "relation": "middle_daughter", "nicknames": ["Sadia"], "category": "family"},
    {"id": "c012", "name": "Ammu", "phone": "01712000012", "relation": "mother", "nicknames": ["Maa"], "category": "family"},
    {"id": "c013", "name": "Abbu", "phone": "01812000013", "relation": "father", "nicknames": ["Baba"], "category": "family"},
    {"id": "c014", "name": "Sasur Abba", "phone": "01718000014", "relation": "father_in_law", "nicknames": ["Shashur"], "category": "family"},
    {"id": "c015", "name": "Sasuri Amma", "phone": "01719000015", "relation": "mother_in_law", "nicknames": ["Shashuri"], "category": "family"},

    # Office & Professional Contexts
    {"id": "c016", "name": "Tareq Boss", "phone": "01713000016", "relation": "supervisor", "nicknames": ["Boss", "Tareq Sir"], "category": "office"},
    {"id": "c017", "name": "Mamun HR", "phone": "01817000017", "relation": "colleague", "nicknames": ["HR Mamun"], "category": "office"},
    {"id": "c018", "name": "Asif Manager", "phone": "01918000018", "relation": "manager", "nicknames": ["Asif Bhai"], "category": "office"},
    {"id": "c019", "name": "Shohel Project Lead", "phone": "01619000019", "relation": "colleague", "nicknames": ["Shohel"], "category": "office"},
    {"id": "c020", "name": "Kamrul Accounts", "phone": "01511000020", "relation": "colleague", "nicknames": ["Kamrul Bhai"], "category": "office"},
    {"id": "c021", "name": "MD Mahbub Sir", "phone": "01711000021", "relation": "executive", "nicknames": ["MD Sir"], "category": "office"},
    {"id": "c022", "name": "Farhana Admin", "phone": "01811000022", "relation": "colleague", "nicknames": ["Farhana Apa"], "category": "office"},
    {"id": "c023", "name": "Tanvir Office", "phone": "01911000023", "relation": "colleague", "nicknames": ["Tanvir Colleague"], "category": "office"},
    {"id": "c024", "name": "Jashim IT Support", "phone": "01611000024", "relation": "colleague", "nicknames": ["IT Jashim"], "category": "office"},
    {"id": "c025", "name": "Rashed Logistics", "phone": "01721000025", "relation": "colleague", "nicknames": ["Rashed"], "category": "office"},

    # Local Trades, Daily Services & Help
    {"id": "c026", "name": "Driver Sohel", "phone": "01821000026", "relation": "driver", "nicknames": ["Gari Driver Sohel"], "category": "service"},
    {"id": "c027", "name": "Electrician Rahim", "phone": "01921000027", "relation": "electrician", "nicknames": ["Mistri Rahim"], "category": "service"},
    {"id": "c028", "name": "Plumber Mizan", "phone": "01621000028", "relation": "plumber", "nicknames": ["Pani Mistri Mizan"], "category": "service"},
    {"id": "c029", "name": "Carpenter Rafiq", "phone": "01731000029", "relation": "carpenter", "nicknames": ["Kaather Mistri"], "category": "service"},
    {"id": "c030", "name": "Grocery Al-Amin", "phone": "01831000030", "relation": "grocer", "nicknames": ["Mudir Dokan Al-Amin"], "category": "service"},
    {"id": "c031", "name": "Security Guard Badal", "phone": "01931000031", "relation": "guard", "nicknames": ["Darowan Badal"], "category": "service"},
    {"id": "c032", "name": "Pressing / Dhopa Kalam", "phone": "01631000032", "relation": "laundry", "nicknames": ["Dhopa Kalam"], "category": "service"},
    {"id": "c033", "name": "AC Technician Rubel", "phone": "01741000033", "relation": "technician", "nicknames": ["AC Rubel"], "category": "service"},
    {"id": "c034", "name": "Gas Delivery Babul", "phone": "01841000034", "relation": "delivery", "nicknames": ["Cylinder Babul"], "category": "service"},
    {"id": "c035", "name": "Fish Seller Manik", "phone": "01941000035", "relation": "vendor", "nicknames": ["Macher Dokan Manik"], "category": "service"},

    # Doctors & Health
    {"id": "c036", "name": "Dr. Mahfuz Dental", "phone": "01751000036", "relation": "doctor", "nicknames": ["Dater Daktar Mahfuz"], "category": "health"},
    {"id": "c037", "name": "Dr. Shamsul Heart", "phone": "01851000037", "relation": "doctor", "nicknames": ["Heart Daktar Shamsul"], "category": "health"},
    {"id": "c038", "name": "Dr. Fatema Gynea", "phone": "01951000038", "relation": "doctor", "nicknames": ["Dr. Fatema"], "category": "health"},
    {"id": "c039", "name": "Dr. Anwar Medicine", "phone": "01651000039", "relation": "doctor", "nicknames": ["Medicine Doctor Anwar"], "category": "health"},
    {"id": "c040", "name": "Square Hospital Emergency", "phone": "01761000040", "relation": "hospital", "nicknames": ["Square Emergency"], "category": "health"},
    {"id": "c041", "name": "Labaid Pharmacy", "phone": "01861000041", "relation": "pharmacy", "nicknames": ["Osudh Dokan Labaid"], "category": "health"},
    {"id": "c042", "name": "Popular Diagnostic Center", "phone": "01961000042", "relation": "diagnostic", "nicknames": ["Popular Lab"], "category": "health"},
    {"id": "c043", "name": "Physiotherapist Hasan", "phone": "01661000043", "relation": "therapist", "nicknames": ["Therapy Hasan"], "category": "health"},

    # Extended Relatives (Bangla kinship network)
    {"id": "c044", "name": "Boro Bhai (Alam)", "phone": "01771000044", "relation": "elder_brother", "nicknames": ["Boro Bhai"], "category": "family"},
    {"id": "c045", "name": "Choto Bon (Salma)", "phone": "01871000045", "relation": "younger_sister", "nicknames": ["Choto Bon"], "category": "family"},
    {"id": "c046", "name": "Mejo Bhai (Belal)", "phone": "01971000046", "relation": "middle_brother", "nicknames": ["Mejo Bhai"], "category": "family"},
    {"id": "c047", "name": "Chacha (Shahid)", "phone": "01671000047", "relation": "paternal_uncle", "nicknames": ["Kaka"], "category": "family"},
    {"id": "c048", "name": "Chachi Khulna", "phone": "01781000048", "relation": "paternal_aunt", "nicknames": ["Chachi"], "category": "family"},
    {"id": "c049", "name": "Boro Mama Sylhet", "phone": "01881000049", "relation": "maternal_uncle", "nicknames": ["Boro Mama"], "category": "family"},
    {"id": "c050", "name": "Choto Mami", "phone": "01981000050", "relation": "maternal_aunt", "nicknames": ["Mami"], "category": "family"},
    {"id": "c051", "name": "Boro Fupu", "phone": "01681000051", "relation": "paternal_aunt", "nicknames": ["Fupu"], "category": "family"},
    {"id": "c052", "name": "Choto Khalamma", "phone": "01791000052", "relation": "maternal_aunt", "nicknames": ["Khala"], "category": "family"},
    {"id": "c053", "name": "Dulabhai Faruk", "phone": "01891000053", "relation": "brother_in_law", "nicknames": ["Dulabhai"], "category": "family"},
    {"id": "c054", "name": "Bhabi (Sultana)", "phone": "01991000054", "relation": "sister_in_law", "nicknames": ["Bhabhi"], "category": "family"},
    {"id": "c055", "name": "Saley (Imran)", "phone": "01691000055", "relation": "brother_in_law", "nicknames": ["Shala Imran"], "category": "family"},
    {"id": "c056", "name": "Nati Aayan", "phone": "01710000056", "relation": "grandson", "nicknames": ["Aayan"], "category": "family"},
    {"id": "c057", "name": "Natni Anika", "phone": "01810000057", "relation": "granddaughter", "nicknames": ["Anika"], "category": "family"},

    # Friends, Alumni & Community
    {"id": "c058", "name": "Zubair BUET", "phone": "01910000058", "relation": "friend", "nicknames": ["Zubair"], "category": "friend"},
    {"id": "c059", "name": "Shakil School Friend", "phone": "01610000059", "relation": "friend", "nicknames": ["Shakil"], "category": "friend"},
    {"id": "c060", "name": "Monir Varsity", "phone": "01720000060", "relation": "friend", "nicknames": ["Monir"], "category": "friend"},
    {"id": "c061", "name": "Hassan College Friend", "phone": "01820000061", "relation": "friend", "nicknames": ["Hassan"], "category": "friend"},
    {"id": "c062", "name": "Nasir Mosque Committee", "phone": "01920000062", "relation": "community", "nicknames": ["Nasir Bhai"], "category": "community"},
    {"id": "c063", "name": "Kader Flat President", "phone": "01620000063", "relation": "community", "nicknames": ["Building President"], "category": "community"},
    {"id": "c064", "name": "Shahidullah Hajj Kafela", "phone": "01730000064", "relation": "community", "nicknames": ["Haji Shahidullah"], "category": "community"},
    {"id": "c065", "name": "Mahbub Morning Walk", "phone": "01830000065", "relation": "friend", "nicknames": ["Mahbub Uncle"], "category": "friend"},

    # Bengali Unicode Names (Elder users with Bangla script contacts)
    {"id": "c066", "name": "তানভীর আহমেদ (বড় ছেলে)", "phone": "01711000001", "relation": "elder_son", "nicknames": ["তানভীর"], "category": "family"},
    {"id": "c067", "name": "রিফাত হাসান (ছোট ছেলে)", "phone": "01819000002", "relation": "younger_son", "nicknames": ["রিফাত"], "category": "family"},
    {"id": "c068", "name": "নাবিলা রহমান", "phone": "01912000003", "relation": "elder_daughter", "nicknames": ["নাবিলা"], "category": "family"},
    {"id": "c069", "name": "রোকেয়া বেগম", "phone": "01552000006", "relation": "spouse", "nicknames": ["গিন্নি", "বউ"], "category": "family"},
    {"id": "c070", "name": "আম্মা", "phone": "01712000012", "relation": "mother", "nicknames": ["আম্মু"], "category": "family"},
    {"id": "c071", "name": "আব্বা", "phone": "01812000013", "relation": "father", "nicknames": ["আব্বু"], "category": "family"},
    {"id": "c072", "name": "বড় মেয়ে", "phone": "01912000003", "relation": "elder_daughter", "nicknames": ["নাবিলা"], "category": "family"},
    {"id": "c073", "name": "ছোট ছেলে", "phone": "01819000002", "relation": "younger_son", "nicknames": ["রিফাত"], "category": "family"},
    {"id": "c074", "name": "ড্রাইভার সোহেল", "phone": "01821000026", "relation": "driver", "nicknames": ["সোহেল"], "category": "service"},
    {"id": "c075", "name": "ইলেকট্রিশিয়ান রহিম", "phone": "01921000027", "relation": "electrician", "nicknames": ["রহিম মিস্ত্রি"], "category": "service"},

    # Standard Common Bangladeshi Names (To make total 100)
    {"id": "c076", "name": "Afsana Mimi", "phone": "01740000076", "relation": "colleague", "nicknames": ["Mimi"], "category": "other"},
    {"id": "c077", "name": "Ahsan Habib", "phone": "01840000077", "relation": "other", "nicknames": ["Habib"], "category": "other"},
    {"id": "c078", "name": "Akram Khan", "phone": "01940000078", "relation": "other", "nicknames": ["Akram"], "category": "other"},
    {"id": "c079", "name": "Anisur Rahman", "phone": "01640000079", "relation": "other", "nicknames": ["Anis"], "category": "other"},
    {"id": "c080", "name": "Arifur Zaman", "phone": "01750000080", "relation": "other", "nicknames": ["Arif"], "category": "other"},
    {"id": "c081", "name": "Azim Uddin", "phone": "01850000081", "relation": "other", "nicknames": ["Azim"], "category": "other"},
    {"id": "c082", "name": "Biplob Kumar", "phone": "01950000082", "relation": "other", "nicknames": ["Biplob"], "category": "other"},
    {"id": "c083", "name": "Delwar Hossain", "phone": "01650000083", "relation": "other", "nicknames": ["Delwar"], "category": "other"},
    {"id": "c084", "name": "Enamul Haque", "phone": "01760000084", "relation": "other", "nicknames": ["Enamul"], "category": "other"},
    {"id": "c085", "name": "Habibur Rahman", "phone": "01860000085", "relation": "other", "nicknames": ["Habib"], "category": "other"},
    {"id": "c086", "name": "Iqbal Bahar", "phone": "01960000086", "relation": "other", "nicknames": ["Iqbal"], "category": "other"},
    {"id": "c087", "name": "Jahidul Islam", "phone": "01660000087", "relation": "other", "nicknames": ["Jahid"], "category": "other"},
    {"id": "c088", "name": "Khaled Mahmud", "phone": "01770000088", "relation": "other", "nicknames": ["Khaled"], "category": "other"},
    {"id": "c089", "name": "Lutfur Rahman", "phone": "01870000089", "relation": "other", "nicknames": ["Lutfur"], "category": "other"},
    {"id": "c090", "name": "Mahbubur Rashid", "phone": "01970000090", "relation": "other", "nicknames": ["Rashid"], "category": "other"},
    {"id": "c091", "name": "Nazrul Islam", "phone": "01670000091", "relation": "other", "nicknames": ["Nazrul"], "category": "other"},
    {"id": "c092", "name": "Omar Faruk", "phone": "01780000092", "relation": "other", "nicknames": ["Faruk"], "category": "other"},
    {"id": "c093", "name": "Pavel Mahmud", "phone": "01880000093", "relation": "other", "nicknames": ["Pavel"], "category": "other"},
    {"id": "c094", "name": "Quazi Shams", "phone": "01980000094", "relation": "other", "nicknames": ["Shams"], "category": "other"},
    {"id": "c095", "name": "Rashedul Karim", "phone": "01680000095", "relation": "other", "nicknames": ["Karim"], "category": "other"},
    {"id": "c096", "name": "Saiful Islam", "phone": "01790000096", "relation": "other", "nicknames": ["Saiful"], "category": "other"},
    {"id": "c097", "name": "Tariqul Islam", "phone": "01890000097", "relation": "other", "nicknames": ["Tariq"], "category": "other"},
    {"id": "c098", "name": "Wahiduzzaman", "phone": "01990000098", "relation": "other", "nicknames": ["Wahid"], "category": "other"},
    {"id": "c099", "name": "Zillur Rahman", "phone": "01690000099", "relation": "other", "nicknames": ["Zillur"], "category": "other"},
    {"id": "c100", "name": "Ziaur Rahman", "phone": "01700000100", "relation": "other", "nicknames": ["Zia"], "category": "other"}
]

with open(os.path.join(DATA_DIR, "contacts_100.json"), "w", encoding="utf-8") as f:
    json.dump(contacts, f, ensure_ascii=False, indent=2)

print(f"Saved {len(contacts)} contacts to contacts_100.json")

# 2. Generate Kinship Knowledge Graph
# Represents the elder user's personal family tree and relationship registry
kinship_graph = {
    "user_profile": {
        "user_name": "Alhaj Rafiqul Islam",
        "age": 68,
        "location": "Mirpur, Dhaka"
    },
    "relations": {
        "elder_son": {
            "bangla_labels": ["বড় ছেলে", "বড় পোলা", "বড় বেটা", "তানভীর"],
            "english_labels": ["boro chele", "elder son", "tanvir"],
            "contact_id": "c001",
            "name": "Tanvir Ahmed",
            "phone": "01711000001"
        },
        "younger_son": {
            "bangla_labels": ["ছোট ছেলে", "ছোট পোলা", "ছোট বেটা", "রিফাত"],
            "english_labels": ["choto chele", "younger son", "rifat"],
            "contact_id": "c002",
            "name": "Rifat Hasan",
            "phone": "01819000002"
        },
        "middle_son": {
            "bangla_labels": ["মেজো ছেলে", "সেজো ছেলে", "কামরুল"],
            "english_labels": ["mejo chele", "sejo chele", "kamrul"],
            "contact_id": "c010",
            "name": "Sejo Chele (Kamrul)",
            "phone": "01814000010"
        },
        "elder_daughter": {
            "bangla_labels": ["বড় মেয়ে", "বড় মাইয়া", "নাবিলা"],
            "english_labels": ["boro meye", "elder daughter", "nabila"],
            "contact_id": "c003",
            "name": "Nabila Rahman",
            "phone": "01912000003"
        },
        "younger_daughter": {
            "bangla_labels": ["ছোট মেয়ে", "ছোট মাইয়া", "তাসনিম"],
            "english_labels": ["choto meye", "younger daughter", "tasnim"],
            "contact_id": "c004",
            "name": "Tasnim Jahan",
            "phone": "01615000004"
        },
        "son_in_law": {
            "bangla_labels": ["বড় জামাই", "জামাই", "ফাহিম"],
            "english_labels": ["boro jamai", "jamai", "fahim"],
            "contact_id": "c005",
            "name": "Fahim Kabir",
            "phone": "01715000005"
        },
        "spouse": {
            "bangla_labels": ["বউ", "স্ত্রী", "গিন্নি", "রোকেয়া"],
            "english_labels": ["wife", "bou", "rokeya"],
            "contact_id": "c006",
            "name": "Rokeya Begum",
            "phone": "01552000006"
        },
        "driver": {
            "bangla_labels": ["ড্রাইভার", "ড্রাইভার সোহেল", "সোহেল"],
            "english_labels": ["driver", "driver sohel", "sohel"],
            "contact_id": "c026",
            "name": "Driver Sohel",
            "phone": "01821000026"
        },
        "electrician": {
            "bangla_labels": ["ইলেকট্রিশিয়ান", "রহিম মিস্ত্রি", "বিদ্যুৎ মিস্ত্রি"],
            "english_labels": ["electrician", "rahim electrician", "mistri"],
            "contact_id": "c027",
            "name": "Electrician Rahim",
            "phone": "01921000027"
        },
        "doctor": {
            "bangla_labels": ["দাঁতের ডাক্তার", "ডাক্তার মাহফুজ", "ডাক্তার"],
            "english_labels": ["dentist", "dr mahfuz", "doctor"],
            "contact_id": "c036",
            "name": "Dr. Mahfuz Dental",
            "phone": "01751000036"
        },
        "supervisor": {
            "bangla_labels": ["বস", "তারেক বস", "তারেক স্যার"],
            "english_labels": ["boss", "tareq boss", "sir"],
            "contact_id": "c016",
            "name": "Tareq Boss",
            "phone": "01713000016"
        }
    },
    "category_groups": {
        "chele": ["elder_son", "younger_son", "middle_son"],
        "meye": ["elder_daughter", "younger_daughter"],
        "jamai": ["son_in_law"]
    }
}

with open(os.path.join(DATA_DIR, "kinship_graph.json"), "w", encoding="utf-8") as f:
    json.dump(kinship_graph, f, ensure_ascii=False, indent=2)

print("Saved kinship_graph.json")

# 3. Create Realistic Mock Android Storage Tree
STORAGE_ROOT = os.path.join(DATA_DIR, "mock_storage")
dirs = [
    os.path.join(STORAGE_ROOT, "DCIM", "Camera"),
    os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Images"),
    os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Documents"),
    os.path.join(STORAGE_ROOT, "Download"),
    os.path.join(STORAGE_ROOT, "Recordings"),
    os.path.join(STORAGE_ROOT, "Documents")
]

for d in dirs:
    os.makedirs(d, exist_ok=True)

# Generate mock files with realistic timestamps
now = datetime.now()

files_manifest = [
    # WhatsApp Images (Son sent recent photo, family photos)
    {
        "path": os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Images", "IMG-20261022-WA0001.jpg"),
        "minutes_ago": 15,
        "content": "JPEG photo received via WhatsApp from younger son Rifat (Grandson playing in park)",
        "sender": "Rifat Hasan (Choto Chele)",
        "tags": ["image", "whatsapp", "photo", "son", "choto_chele", "grandson"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Images", "IMG-20261022-WA0002.jpg"),
        "minutes_ago": 45,
        "content": "JPEG photo received via WhatsApp from elder daughter Nabila (Family gathering)",
        "sender": "Nabila Rahman (Boro Meye)",
        "tags": ["image", "whatsapp", "photo", "family", "boro_meye"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Images", "IMG-20261021-WA0003.jpg"),
        "minutes_ago": 1440, # 1 day ago
        "content": "JPEG medicine voucher photo sent on WhatsApp",
        "sender": "Dr. Mahfuz Dental",
        "tags": ["image", "whatsapp", "voucher", "medicine"]
    },
    
    # WhatsApp Documents (Boss sent invoice, son sent ticket)
    {
        "path": os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Documents", "Invoice_Project_Alpha_Oct2026.pdf"),
        "minutes_ago": 90,
        "content": "PDF invoice received from Tareq Boss for Q3 consulting",
        "sender": "Tareq Boss",
        "tags": ["pdf", "document", "invoice", "boss", "office", "bill"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "WhatsApp", "Media", "WhatsApp Documents", "Biman_Bangladesh_E-Ticket_Dhaka_CoxsBazar.pdf"),
        "minutes_ago": 1200,
        "content": "Air ticket PDF sent by elder son Tanvir",
        "sender": "Tanvir Ahmed",
        "tags": ["pdf", "document", "ticket", "travel", "tanvir", "son"]
    },
    
    # DCIM Camera (Photos taken on phone camera)
    {
        "path": os.path.join(STORAGE_ROOT, "DCIM", "Camera", "IMG_20261022_162000.jpg"),
        "minutes_ago": 30, # Latest camera photo
        "content": "Camera photo taken 30 minutes ago of electricity meter reading",
        "sender": "Self",
        "tags": ["image", "camera", "photo", "meter", "electricity"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "DCIM", "Camera", "IMG_20261022_111500.jpg"),
        "minutes_ago": 320,
        "content": "Camera photo taken this morning of house repair work",
        "sender": "Self",
        "tags": ["image", "camera", "photo", "repair"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "DCIM", "Camera", "IMG_20261021_093000.jpg"),
        "minutes_ago": 1800, # Yesterday camera photo
        "content": "Camera photo of hand-written prescription from yesterday",
        "sender": "Self",
        "tags": ["image", "camera", "photo", "prescription", "doctor"]
    },

    # Downloads
    {
        "path": os.path.join(STORAGE_ROOT, "Download", "DESCO_Electricity_Bill_October_2026.pdf"),
        "minutes_ago": 240,
        "content": "PDF copy of DESCO October electricity bill",
        "sender": "DESCO Portal",
        "tags": ["pdf", "download", "bill", "electricity", "desco", "কাগজ"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "Download", "Smart_NID_Card_Rafiqul_Islam.pdf"),
        "minutes_ago": 4320, # 3 days ago
        "content": "PDF copy of Smart National ID card",
        "sender": "Election Commission",
        "tags": ["pdf", "download", "nid", "identity", "ভোট"]
    },
    {
        "path": os.path.join(STORAGE_ROOT, "Download", "Square_Hospital_Prescription_Dr_Shamsul.pdf"),
        "minutes_ago": 1400, # Yesterday
        "content": "PDF prescription downloaded from Square Hospital patient portal",
        "sender": "Square Hospital",
        "tags": ["pdf", "download", "prescription", "doctor", "health", "প্রেসক্রিপশন"]
    },

    # Recordings
    {
        "path": os.path.join(STORAGE_ROOT, "Recordings", "VoiceNote_Building_Meeting_Oct22.m4a"),
        "minutes_ago": 180,
        "content": "Audio recording of flat owners association meeting",
        "sender": "Self",
        "tags": ["audio", "recording", "voice", "meeting"]
    }
]

created_count = 0
for item in files_manifest:
    fpath = item["path"]
    with open(fpath, "w", encoding="utf-8") as f:
        f.write(f"=== PULSAR TEST FILE ===\n")
        f.write(f"Filename: {os.path.basename(fpath)}\n")
        f.write(f"Sender: {item['sender']}\n")
        f.write(f"Tags: {', '.join(item['tags'])}\n")
        f.write(f"Content: {item['content']}\n")
    
    # Modify file mtime to reflect realistic timestamps
    target_time = time.time() - (item["minutes_ago"] * 60)
    os.utime(fpath, (target_time, target_time))
    created_count += 1

print(f"Created {created_count} test files in mock Android storage tree.")
