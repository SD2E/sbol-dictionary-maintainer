from setuptools import setup, find_packages

setup(
    name='sd2_dictionary_writer',
    package_dir={'': 'src'},
    packages=find_packages('src'),
    install_requires=[
        'google-api-python-client',
        'google-auth-httplib2',
        'google-auth-oauthlib',
        'functools'
    ]
)