from setuptools import setup, find_packages

setup(
    name='sd2_dictionary_writer',
    package_dir={'': 'src'},
    packages=find_packages('src'),
    zip_safe=True,
    install_requires=[
        'google-auth',
        'google-auth-oauthlib',
        'google-api-python-client',
        'google-auth-httplib2'
    ]
)
