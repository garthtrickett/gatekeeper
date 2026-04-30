#!/usr/bin/env python3
import sys
import json
import os
import re

def strip_and_map(t):
    s = []
    m =[]
    for i, c in enumerate(t):
        if not c.isspace():
            s.append(c)
            m.append(i)
    return "".join(s), m

def find_block_end(text, start_idx):
    i = start_idx
    brace_depth = 0
    in_str = False
    in_char = False
    in_line_comment = False
    in_block_comment = False
    found_first_brace = False

    while i < len(text):
        c = text[i]
        next_c = text[i+1] if i+1 < len(text) else ''

        if in_line_comment:
            if c == '\n': in_line_comment = False
            i += 1
            continue
        if in_block_comment:
            if c == '*' and next_c == '/':
                in_block_comment = False
                i += 2
                continue
            i += 1
            continue
        if in_str:
            if c == '\\': i += 2; continue
            if c == '"': in_str = False
            i += 1
            continue
        if in_char:
            if c == '\\': i += 2; continue
            if c == "'": in_char = False
            i += 1
            continue

        if c == '/' and next_c == '/':
            in_line_comment = True
            i += 2
            continue
        if c == '/' and next_c == '*':
            in_block_comment = True
            i += 2
            continue
        if c == '"':
            in_str = True
            i += 1
            continue
        if c == "'":
            in_char = True
            i += 1
            continue

        if c == '{':
            brace_depth += 1
            found_first_brace = True
        elif c == '}':
            brace_depth -= 1

        if found_first_brace and brace_depth == 0:
            return i

        i += 1
    return -1

def apply_smart_replace(text, search, replace):
    if not search:
        if not text.strip():
            return replace
        return text + "\n" + replace

    target_s, target_m = strip_and_map(text)
    search_s, _ = strip_and_map(search)

    idx = target_s.find(search_s)
    if idx == -1:
        if search in text:
            return text.replace(search, replace, 1)
        raise Exception("Could not find match for smart_replace")

    start_orig = target_m[idx]
    end_orig = target_m[idx + len(search_s) - 1]

    return text[:start_orig] + replace + text[end_orig + 1:]

def apply_entity_replace(text, entity_type, name, replace):
    if entity_type == "replace_function":
        pattern = r"(?:override\s+|private\s+|public\s+|protected\s+|internal\s+|suspend\s+|inline\s+)*fun\s+(?:[\w<>_,\s]*\.)?" + re.escape(name) + r"\b"
    else:
        pattern = r"(?:data\s+|sealed\s+|open\s+|abstract\s+|inner\s+|enum\s+|annotation\s+)?(?:class|interface|object)\s+" + re.escape(name) + r"\b"

    match = re.search(pattern, text)
    if not match:
        raise Exception(f"Could not find entity declaration for '{name}'")

    start_idx = match.start()
    end_idx = find_block_end(text, start_idx)

    if end_idx == -1:
        raise Exception(f"Could not find matching brackets for entity '{name}'")

    return text[:start_idx] + replace + text[end_idx + 1:]

def main():
    if len(sys.argv) < 2:
        print("Usage: python apply_patch.py <path_to_json>")
        sys.exit(1)

    json_path = sys.argv[1]
    with open(json_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    print(f"\n🤖 Applying Patch: {data.get('summary', 'No summary provided')}\n")

    success_count = 0
    fail_count = 0

    for file_info in data.get('files',[]):
        file_path = file_info['file_path']
        edits = file_info.get('edits',[])
        
        if 'code_diff' in file_info:
            print(f"⚠️ {file_path}: Uses old code_diff format. Ignoring in smart patcher.")
            continue

        try:
            if os.path.exists(file_path):
                with open(file_path, 'r', encoding='utf-8') as f:
                    text = f.read()
            else:
                text = ""
                os.makedirs(os.path.dirname(file_path), exist_ok=True)

            for edit in edits:
                edit_type = edit.get('type')
                if edit_type == 'smart_replace':
                    text = apply_smart_replace(text, edit.get('search', ''), edit['replace'])
                elif edit_type in ('replace_function', 'replace_class'):
                    text = apply_entity_replace(text, edit_type, edit['name'], edit['replace'])
                else:
                    raise Exception(f"Unknown edit type: {edit_type}")

            with open(file_path, 'w', encoding='utf-8') as f:
                f.write(text)
            
            print(f"✅ {file_path} updated successfully.")
            success_count += 1
        except Exception as e:
            print(f"❌ Failed to patch {file_path}: {e}")
            fail_count += 1

    print(f"\nDone. {success_count} files updated, {fail_count} files failed.")
    if fail_count > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()
