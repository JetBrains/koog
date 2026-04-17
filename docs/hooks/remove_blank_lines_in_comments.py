import re

def on_page_markdown(markdown, **kwargs):
    """
    Remove blank lines inside HTML comments since it can break indented blocks (tab groups)
    when such a comment is put there.
    """
    def fix_indented_comment(match):
        indent = match.group(1)
        body = match.group(2)
        # Remove blank lines inside the comment body
        lines = body.split('\n')
        fixed = [line for line in lines if line.strip() != '']
        return indent + '<!--- ' + '\n'.join(fixed) + ' -->'

    # Match indented HTML comments that span multiple lines
    return re.sub(
        r'^([ ]{4,})<!---\s(.*?)\s-->',
        fix_indented_comment,
        markdown,
        flags=re.MULTILINE | re.DOTALL,
    )
