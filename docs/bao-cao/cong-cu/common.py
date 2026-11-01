# -*- coding: utf-8 -*-
"""Đọc docx thành danh sách phần tử theo thứ tự (đoạn văn ngoài bảng, đoạn văn trong ô bảng) và chuẩn hóa văn bản."""
import re
from docx.oxml.ns import qn


def ptext(p):
    out = []
    for el in p.iter():
        if el.tag == qn('w:t'):
            out.append(el.text or '')
        elif el.tag == qn('w:tab'):
            out.append('\t')
        elif el.tag in (qn('w:br'), qn('w:cr')) and el.get(qn('w:type')) in (None, 'textWrapping'):
            out.append('\n')
    return ''.join(out)


def items_of(body):
    """[(kind, element, text, table_el, tc_el)]"""
    items = []

    def walk(parent, tbl=None, tc=None):
        for el in parent.iterchildren():
            if el.tag == qn('w:p'):
                items.append(('c' if tc is not None else 'p', el, ptext(el), tbl, tc))
            elif el.tag == qn('w:tbl'):
                for tr in el.iterchildren(qn('w:tr')):
                    for cell in tr.iterchildren(qn('w:tc')):
                        walk(cell, el, cell)
            elif el.tag == qn('w:sdt'):
                c = el.find(qn('w:sdtContent'))
                if c is not None:
                    walk(c, tbl, tc)
    walk(body)
    return items


def norm(s):
    s = s.replace(' ', ' ')
    s = re.sub(r'^[\s*•·\-–]*\s*', '', s) if re.match(r'^\s*[*•·]\s', s) else s
    s = re.sub(r'\s+', ' ', s).strip()
    return s


def has_drawing(p):
    return p.find('.//' + qn('w:drawing')) is not None or p.find('.//' + qn('w:pict')) is not None


RPR_ORDER = ['rStyle', 'rFonts', 'b', 'bCs', 'i', 'iCs', 'caps', 'smallCaps', 'strike', 'dstrike', 'outline', 'shadow',
             'emboss', 'imprint', 'noProof', 'snapToGrid', 'vanish', 'webHidden', 'color', 'spacing', 'w', 'kern',
             'position', 'sz', 'szCs', 'highlight', 'u', 'effect', 'bdr', 'shd', 'fitText', 'vertAlign', 'rtl', 'cs',
             'em', 'lang', 'eastAsianLayout', 'specVanish', 'oMath']
SETTINGS_AFTER_UPDATE = ['hdrShapeDefaults', 'footnotePr', 'endnotePr', 'compat', 'docVars', 'rsids', 'mathPr',
                         'attachedSchema', 'themeFontLang', 'clrSchemeMapping', 'doNotIncludeSubdocsInStats',
                         'doNotAutoCompressPictures', 'forceUpgrade', 'captions', 'readModeInkLockDown', 'smartTagType',
                         'schemaLibrary', 'shapeDefaults', 'doNotEmbedSmartTags', 'decimalSymbol', 'listSeparator']


def _local(tag):
    return tag.split('}')[1]


def rpr_set(rpr, name, val=None):
    """Thêm/đặt phần tử trong rPr đúng thứ tự lược đồ OOXML."""
    from docx.oxml import OxmlElement
    for old in rpr.findall(qn('w:' + name)):
        rpr.remove(old)
    el = OxmlElement('w:' + name)
    if val is not None:
        el.set(qn('w:val'), val)
    pos = RPR_ORDER.index(name)
    for k, ch in enumerate(list(rpr)):
        if _local(ch.tag) in RPR_ORDER and RPR_ORDER.index(_local(ch.tag)) > pos:
            ch.addprevious(el)
            return el
    rpr.append(el)
    return el


def set_update_fields(settings_el):
    from docx.oxml import OxmlElement
    for old in settings_el.findall(qn('w:updateFields')):
        settings_el.remove(old)
    el = OxmlElement('w:updateFields')
    el.set(qn('w:val'), 'true')
    for ch in settings_el:
        if _local(ch.tag) in SETTINGS_AFTER_UPDATE:
            ch.addprevious(el)
            return
    settings_el.append(el)
